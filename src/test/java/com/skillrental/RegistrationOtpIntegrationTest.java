package com.skillrental;

import com.skillrental.entity.AccountStatus;
import com.skillrental.entity.CustomerProfile;
import com.skillrental.entity.Role;
import com.skillrental.entity.User;
import com.skillrental.repository.CustomerProfileRepository;
import com.skillrental.repository.UserRepository;
import com.skillrental.service.AuthService;
import com.skillrental.service.AuthService.OtpResendResult;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.mail.username=test-sender@example.test",
        "spring.mail.password=test-password"
})
@AutoConfigureMockMvc
class RegistrationOtpIntegrationTest {
    private static final Pattern OTP_IN_MAIL = Pattern.compile("SkillShare của bạn: ([0-9]{6})");

    @Autowired private AuthService auth;
    @Autowired private UserRepository users;
    @Autowired private CustomerProfileRepository profiles;
    @Autowired private PasswordEncoder passwords;
    @Autowired private MockMvc mvc;
    @MockBean private JavaMailSender mailSender;
    private final AtomicReference<String> lastMailBody = new AtomicReference<>();
    private final AtomicReference<MimeMessage> lastMessage = new AtomicReference<>();

    @BeforeEach
    void mockSmtp() throws Exception {
        reset(mailSender);
        lastMailBody.set(null);
        lastMessage.set(null);
        stubAcceptedSmtp();
    }

    private void stubAcceptedSmtp() throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(i -> {
            MimeMessage message = i.getArgument(0);
            lastMailBody.set(message.getContent().toString());
            lastMessage.set(message);
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
    }

    private String freshEmail() {
        return "otp-" + UUID.randomUUID() + "@example.test";
    }

    private String otp() {
        assertNotNull(lastMailBody.get(), "No message was passed to JavaMailSender");
        Matcher match = OTP_IN_MAIL.matcher(lastMailBody.get());
        assertTrue(match.find(), "Email must contain a six-digit OTP");
        return match.group(1);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registerPost(
            MockHttpSession session, String email, String code) {
        var request = post("/register").with(csrf());
        if (session != null) request.session(session);
        addFormParams(request, email);
        if (code != null) request.param("otp", code);
        return request;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder sendPost(String email) {
        var request = post("/register/send-otp").with(csrf());
        addFormParams(request, email);
        return request;
    }

    private void addFormParams(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String email) {
        request.param("fullName", "Khách hàng OTP")
                .param("email", email)
                .param("password", "StrongPwd#123")
                .param("confirmPassword", "StrongPwd#123")
                .param("acceptedTerms", "true");
    }

    private User sendOtp(String email) throws Exception {
        mvc.perform(sendPost(email)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }

    @Test
    void sendOtpCreatesPendingAccountAndFinalRegistrationActivatesIt() throws Exception {
        String email = freshEmail();
        var response = mvc.perform(sendPost(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.resendAvailableAtEpochMillis").isNumber())
                .andReturn();

        User pending = users.findByEmailIgnoreCase(email).orElseThrow();
        assertEquals(AccountStatus.PENDING_VERIFICATION, pending.getAccountStatus());
        assertFalse(pending.getEmailVerified());
        assertNotNull(pending.getEmailVerificationToken());
        assertNotEquals(otp(), pending.getEmailVerificationToken());
        assertTrue(passwords.matches(otp(), pending.getEmailVerificationToken()));
        assertTrue(profiles.findByUser(pending).isPresent());
        assertEquals(email, lastMessage.get().getRecipients(MimeMessage.RecipientType.TO)[0].toString());

        MockHttpSession session = (MockHttpSession) response.getRequest().getSession(false);
        mvc.perform(registerPost(session, email, otp()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("success", "Đăng ký tài khoản thành công"));
        User active = users.findById(pending.getId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, active.getAccountStatus());
        assertTrue(active.getEmailVerified());
        assertNull(active.getEmailVerificationToken());
        assertTrue(passwords.matches("StrongPwd#123", active.getPassword()));
    }

    @Test
    void missingOtpIsRejectedWithoutCreatingAccount() throws Exception {
        String email = freshEmail();
        mvc.perform(registerPost(null, email, null))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Vui lòng gửi mã OTP để xác minh email trước khi đăng ký")));
        assertTrue(users.findByEmailIgnoreCase(email).isEmpty());
    }

    @Test
    void otpCannotBeUsedForChangedEmail() throws Exception {
        String email = freshEmail();
        var sent = mvc.perform(sendPost(email)).andReturn();
        String code = otp();
        mvc.perform(registerPost((MockHttpSession) sent.getRequest().getSession(false), freshEmail(), code))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Email đã thay đổi sau khi gửi mã")));
        assertEquals(AccountStatus.PENDING_VERIFICATION,
                users.findByEmailIgnoreCase(email).orElseThrow().getAccountStatus());
    }

    @Test
    void wrongOtpExpiresAfterFiveMinutesAndLocksAfterFiveFailures() throws Exception {
        String email = freshEmail();
        User pending = sendOtp(email);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("pendingRegistrationEmail", email);
        String wrong = otp().equals("000000") ? "000001" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(registerPost(session, email, wrong)).andExpect(status().isOk());
        }
        assertEquals(5, users.findById(pending.getId()).orElseThrow().getEmailVerificationAttempts());
        mvc.perform(registerPost(session, email, otp()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("quá 5 lần")));

        pending = users.findById(pending.getId()).orElseThrow();
        pending.setEmailVerificationAttempts(0);
        pending.setEmailVerificationExpiresAt(LocalDateTime.now().minusSeconds(1));
        users.saveAndFlush(pending);
        mvc.perform(registerPost(session, email, otp()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OTP đã hết hạn")));
        assertEquals(AccountStatus.PENDING_VERIFICATION,
                users.findById(pending.getId()).orElseThrow().getAccountStatus());
    }

    @Test
    void cooldownIsDatabaseEnforcedAcrossSessionsAndReloadKeepsTimer() throws Exception {
        String email = freshEmail();
        var sent = mvc.perform(sendPost(email))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) sent.getRequest().getSession(false);

        mvc.perform(get("/register").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("resendAvailableAtEpochMillis")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gửi lại mã sau")));
        mvc.perform(sendPost(email))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.resendAvailableAtEpochMillis").isNumber());

        User pending = users.findByEmailIgnoreCase(email).orElseThrow();
        pending.setEmailVerificationLastSentAt(LocalDateTime.now().minusSeconds(61));
        users.saveAndFlush(pending);
        mvc.perform(sendPost(email)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void smtpFailureDoesNotClaimSuccessOrReplacePreviouslyIssuedOtp() throws Exception {
        String email = freshEmail();
        doThrow(new MailSendException("SMTP offline")).when(mailSender).send(any(MimeMessage.class));
        mvc.perform(sendPost(email))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "SMTP không chấp nhận email OTP. Hãy kiểm tra cấu hình Gmail/App Password rồi thử lại."));
        assertTrue(users.findByEmailIgnoreCase(email).isEmpty());

        reset(mailSender);
        stubAcceptedSmtp();
        User pending = sendOtp(email);
        String previousOtp = otp();
        pending.setEmailVerificationLastSentAt(LocalDateTime.now().minusSeconds(61));
        users.saveAndFlush(pending);
        doThrow(new MailSendException("SMTP offline")).when(mailSender).send(any(MimeMessage.class));
        mvc.perform(sendPost(email)).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
        assertTrue(passwords.matches(previousOtp,
                users.findById(pending.getId()).orElseThrow().getEmailVerificationToken()));
    }

    @Test
    void duplicateSubmissionDoesNotCreateDuplicateUserAndActiveEmailCannotRequestOtp() throws Exception {
        String email = freshEmail();
        var sent = mvc.perform(sendPost(email)).andReturn();
        MockHttpSession session = (MockHttpSession) sent.getRequest().getSession(false);
        String code = otp();
        mvc.perform(registerPost(session, email, code)).andExpect(status().is3xxRedirection());
        long count = users.count();

        mvc.perform(registerPost(session, email, code))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Vui lòng gửi mã OTP để xác minh email trước khi đăng ký")));
        assertEquals(count, users.count());
        mvc.perform(sendPost(email))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void existingPendingAccountCanContinueOnRegisterPage() throws Exception {
        String email = freshEmail();
        User legacyPending = users.saveAndFlush(User.builder()
                .fullName("Khách cũ")
                .email(email)
                .password(passwords.encode("OldPassword#123"))
                .role(Role.CUSTOMER)
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .emailVerified(false)
                .emailVerificationToken(UUID.randomUUID().toString())
                .emailVerificationExpiresAt(LocalDateTime.now().plusHours(1))
                .build());
        profiles.save(CustomerProfile.builder().user(legacyPending).location("TP. Hồ Chí Minh").build());

        var sent = mvc.perform(sendPost(email)).andExpect(status().isOk()).andReturn();
        assertEquals(legacyPending.getId(),
                users.findByEmailIgnoreCase(email).orElseThrow().getId());
        mvc.perform(registerPost((MockHttpSession) sent.getRequest().getSession(false), email, otp()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void legacyOtpPageRedirectsToRegisterAndAllPostEndpointsRequireCsrf() throws Exception {
        mvc.perform(get("/verify-otp")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/register"));
        var noCsrf = post("/register/send-otp");
        addFormParams(noCsrf, freshEmail());
        mvc.perform(noCsrf)
                .andExpect(status().isForbidden());
    }
}
