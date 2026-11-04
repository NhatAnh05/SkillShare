package com.skillrental;

import com.skillrental.entity.AccountStatus;
import com.skillrental.entity.Role;
import com.skillrental.entity.User;
import com.skillrental.repository.UserRepository;
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

import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Integration coverage for the password-reset UI, CSRF, mail delivery and single-use links. */
@SpringBootTest(properties = {
        "spring.mail.username=sender@example.test",
        "spring.mail.password=test-app-password"
})
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;
    @MockBean private JavaMailSender sender;

    @BeforeEach
    void acceptTestMail() {
        reset(sender);
        when(sender.createMimeMessage()).thenAnswer(invocation ->
                new MimeMessage(Session.getInstance(new Properties())));
        doNothing().when(sender).send(any(MimeMessage.class));
    }

    private User createUser(AccountStatus status) {
        String email = "reset-" + UUID.randomUUID() + "@example.test";
        return users.saveAndFlush(User.builder().fullName("Password Reset User")
                .email(email).password(passwords.encode("old-password-123"))
                .role(Role.CUSTOMER).accountStatus(status).emailVerified(true).build());
    }

    @Test
    void forgotPageContainsCsrfFormAndNeverRendersDemoRecoveryLink() throws Exception {
        mvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("resetDemoLink"))))
                .andExpect(content().string(not(containsString("Token demo"))));
    }

    @Test
    void forgotSubmissionWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/forgot-password").param("email", "random@example.test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetLinkIsSentAndWorksOnlyOnce() throws Exception {
        User registered = createUser(AccountStatus.ACTIVE);
        mvc.perform(post("/forgot-password").with(csrf()).param("email", registered.getEmail()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attributeExists("success"));

        verify(sender).send(any(MimeMessage.class));
        String token = users.findById(registered.getId()).orElseThrow().getResetToken();
        assertNotNull(token);
        mvc.perform(get("/forgot-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("Token demo"))));

        mvc.perform(post("/reset-password").with(csrf())
                        .param("token", token)
                        .param("newPassword", "new-password-456")
                        .param("confirmPassword", "new-password-456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        User updated = users.findById(registered.getId()).orElseThrow();
        assertTrue(passwords.matches("new-password-456", updated.getPassword()));
        assertFalse(passwords.matches("old-password-123", updated.getPassword()));
        assertNull(updated.getResetToken());
        assertNull(updated.getResetTokenExpiresAt());

        mvc.perform(post("/reset-password").with(csrf())
                        .param("token", token)
                        .param("newPassword", "another-password-456")
                        .param("confirmPassword", "another-password-456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertTrue(passwords.matches("new-password-456",
                users.findById(registered.getId()).orElseThrow().getPassword()));
    }

    @Test
    void unknownEmailHasSamePublicResponseAndNoMailSent() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf())
                        .param("email", "unknown-" + UUID.randomUUID() + "@example.test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void smtpFailureIsReportedAndNewTokenIsRolledBack() throws Exception {
        User registered = createUser(AccountStatus.ACTIVE);
        doThrow(new MailSendException("simulated SMTP rejection"))
                .when(sender).send(any(MimeMessage.class));

        mvc.perform(post("/forgot-password").with(csrf()).param("email", registered.getEmail()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertNull(users.findById(registered.getId()).orElseThrow().getResetToken());
    }

    @Test
    void inactiveAccountCannotRequestRecoveryOrUseExistingToken() throws Exception {
        User locked = createUser(AccountStatus.LOCKED);
        mvc.perform(post("/forgot-password").with(csrf()).param("email", locked.getEmail()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));
        verify(sender, never()).send(any(MimeMessage.class));

        String token = UUID.randomUUID().toString();
        locked.setResetToken(token);
        locked.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(20));
        users.saveAndFlush(locked);
        mvc.perform(post("/reset-password").with(csrf())
                        .param("token", token)
                        .param("newPassword", "new-password-456")
                        .param("confirmPassword", "new-password-456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertTrue(passwords.matches("old-password-123", users.findById(locked.getId()).orElseThrow().getPassword()));
    }
}
