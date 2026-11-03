package com.skillrental.service;

import com.skillrental.dto.RegisterRequest;
import com.skillrental.entity.*;
import com.skillrental.repository.*;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository users;
    private final CustomerProfileRepository customers;
    private final ProviderProfileRepository providers;
    private final TermsAcceptanceRepository termsAcceptances;
    private final PolicyConsentService policyConsent;
    private final PasswordEncoder encoder;
    private final JavaMailSender mailSender;
    @Value("${app.base-url:http://localhost:8080}") private String baseUrl;
    @Value("${spring.mail.username:}") private String mailUsername;
    @Value("${spring.mail.password:}") private String mailPassword;

    private static final SecureRandom OTP_RANDOM = new SecureRandom();
    private static final int OTP_TTL_MINUTES = 5;
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 60;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public enum OtpVerificationResult { VERIFIED, INVALID, EXPIRED, TOO_MANY_ATTEMPTS }
    public enum OtpResendResult { SENT, TOO_SOON, NOT_ELIGIBLE }
    public record OtpPageState(boolean eligible, boolean previouslySent, boolean otpUsable,
                               boolean resendCooldownActive, String fullName, Role role, LocalDateTime resendAvailableAt) {}

    public static class OtpEmailDeliveryException extends IllegalStateException {
        public OtpEmailDeliveryException(String message) {
            super(message);
        }
    }

    public static class PasswordResetEmailDeliveryException extends IllegalStateException {
        public PasswordResetEmailDeliveryException(String message) {
            super(message);
        }
    }

    @Transactional
    public OtpResendResult sendRegistrationOtp(RegisterRequest form) {
        validateRegistrationForm(form);
        String email = normalizeEmail(form.getEmail());
        User user = users.findByEmailForOtpUpdate(email).orElse(null);
        if (user != null && !isPendingVerification(user)) {
            throw new IllegalArgumentException("Email đã được sử dụng bởi tài khoản đã đăng ký.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (user != null && user.getEmailVerificationLastSentAt() != null
                && user.getEmailVerificationLastSentAt().plusSeconds(OTP_RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            return OtpResendResult.TOO_SOON;
        }

        Role selectedRole = registrationRole(form);
        if (user == null) {
            user = users.saveAndFlush(User.builder()
                    .fullName(form.getFullName().strip())
                    .email(email)
                    .password(encoder.encode(form.getPassword()))
                    .role(selectedRole)
                    .accountStatus(AccountStatus.PENDING_VERIFICATION)
                    .emailVerified(false)
                    .emailVerificationAttempts(0)
                    .avatarUrl("https://i.pravatar.cc/150?img=33")
                    .bio(selectedRole == Role.PROVIDER ? "Provider của Skill Rental" : "Customer của Skill Rental")
                    .build());
        }
        // A role change for an unverified account requires a new OTP, subject to the same cooldown.
        user.setRole(selectedRole);
        ensureRegistrationProfile(user);

        String previousHash = user.getEmailVerificationToken();
        String otp = newOtp();
        while (previousHash != null && previousHash.startsWith("$2") && encoder.matches(otp, previousHash)) {
            otp = newOtp();
        }

        user.setEmailVerificationToken(encoder.encode(otp));
        user.setEmailVerificationExpiresAt(now.plusMinutes(OTP_TTL_MINUTES));
        user.setEmailVerificationAttempts(0);
        user.setEmailVerificationLastSentAt(now);
        users.saveAndFlush(user);

        sendRegistrationOtpMail(user.getEmail(), "Mã OTP xác minh đăng ký SkillShare",
                otpEmailBody(user.getFullName(), otp));

        LocalDateTime acceptedAt = LocalDateTime.now();
        user.setEmailVerificationExpiresAt(acceptedAt.plusMinutes(OTP_TTL_MINUTES));
        user.setEmailVerificationLastSentAt(acceptedAt);
        users.saveAndFlush(user);
        return OtpResendResult.SENT;
    }

    @Transactional(readOnly = true)
    public OtpPageState getRegistrationOtpPageState(String email) {
        LocalDateTime now = LocalDateTime.now();
        User user = email == null || email.isBlank()
                ? null : users.findByEmailIgnoreCase(normalizeEmail(email)).orElse(null);
        if (!isPendingVerification(user)) {
            return new OtpPageState(false, false, false, false, null, null, now);
        }

        LocalDateTime resendAvailableAt = user.getEmailVerificationLastSentAt() == null
                ? now : user.getEmailVerificationLastSentAt().plusSeconds(OTP_RESEND_COOLDOWN_SECONDS);
        int attempts = user.getEmailVerificationAttempts() == null ? 0 : user.getEmailVerificationAttempts();
        boolean previouslySent = user.getEmailVerificationLastSentAt() != null;
        boolean otpUsable = previouslySent && user.getEmailVerificationToken() != null
                && user.getEmailVerificationExpiresAt() != null
                && user.getEmailVerificationExpiresAt().isAfter(now)
                && attempts < OTP_MAX_ATTEMPTS;
        return new OtpPageState(true, previouslySent, otpUsable,
                resendAvailableAt.isAfter(now), user.getFullName(), user.getRole(), resendAvailableAt);
    }

    @Transactional
    public OtpVerificationResult completeRegistration(RegisterRequest form, String sessionEmail, String otp) {
        validateRegistrationForm(form);
        String email = normalizeEmail(form.getEmail());
        if (sessionEmail == null || !normalizeEmail(sessionEmail).equals(email)) {
            throw new IllegalArgumentException("Email đã thay đổi sau khi gửi mã. Hãy gửi mã OTP mới cho email hiện tại.");
        }
        if (otp == null || !otp.matches("[0-9]{6}")) return OtpVerificationResult.INVALID;
        User user = users.findByEmailForOtpUpdate(email).orElse(null);
        if (!isPendingVerification(user)) return OtpVerificationResult.INVALID;
        if (user.getRole() != registrationRole(form)) {
            throw new IllegalArgumentException("Loại tài khoản đã thay đổi. Hãy gửi mã OTP mới cho lựa chọn hiện tại.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getEmailVerificationExpiresAt() == null
                || !user.getEmailVerificationExpiresAt().isAfter(now)) return OtpVerificationResult.EXPIRED;
        int failed = user.getEmailVerificationAttempts() == null ? 0 : user.getEmailVerificationAttempts();
        if (failed >= OTP_MAX_ATTEMPTS) return OtpVerificationResult.TOO_MANY_ATTEMPTS;
        String hashedOtp = user.getEmailVerificationToken();
        if (hashedOtp == null || !hashedOtp.startsWith("$2") || !encoder.matches(otp, hashedOtp)) {
            failed++;
            user.setEmailVerificationAttempts(failed);
            users.save(user);
            return failed >= OTP_MAX_ATTEMPTS
                    ? OtpVerificationResult.TOO_MANY_ATTEMPTS : OtpVerificationResult.INVALID;
        }
        user.setFullName(form.getFullName().strip());
        user.setPassword(encoder.encode(form.getPassword()));
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);
        user.setEmailVerificationAttempts(0);
        user.setEmailVerificationLastSentAt(null);
        users.save(user);
        policyConsent.snapshot("TERMS_OF_USE");
        String currentVersion = PolicyVersion.CURRENT;
        boolean hasCurrentAcceptance = termsAcceptances
                .findFirstByUserAndPolicyTypeOrderByAcceptedAtDesc(user, "TERMS_OF_USE")
                .map(acceptance -> currentVersion.equals(acceptance.getPolicyVersion()))
                .orElse(false);
        if (!hasCurrentAcceptance) {
            termsAcceptances.save(TermsAcceptance.builder().user(user).policyType("TERMS_OF_USE")
                    .policyVersion(currentVersion).source("REGISTRATION").build());
        }
        return OtpVerificationResult.VERIFIED;
    }

    private void validateRegistrationForm(RegisterRequest form) {
        if (form == null || form.getFullName() == null || form.getFullName().isBlank()
                || form.getFullName().strip().length() > 120) {
            throw new IllegalArgumentException("Vui lòng nhập họ tên hợp lệ (tối đa 120 ký tự).");
        }
        if (form.getEmail() == null || !EMAIL_PATTERN.matcher(form.getEmail().strip()).matches()
                || form.getEmail().strip().length() > 160) {
            throw new IllegalArgumentException("Email không hợp lệ.");
        }
        if (form.getPassword() == null || form.getPassword().length() < 8 || form.getPassword().length() > 72) {
            throw new IllegalArgumentException("Mật khẩu phải có từ 8 đến 72 ký tự.");
        }
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }
        if (form.getRole() == null) {
            throw new IllegalArgumentException("Vui lòng chọn Customer hoặc Provider.");
        }
        if (!form.isAcceptedTerms()) {
            throw new IllegalArgumentException("Bạn phải đồng ý điều khoản.");
        }
    }

    private Role registrationRole(RegisterRequest form) {
        return switch (form.getRole()) {
            case CUSTOMER -> Role.CUSTOMER;
            case PROVIDER -> Role.PROVIDER;
        };
    }

    private void ensureRegistrationProfile(User user) {
        if (user.getRole() == Role.PROVIDER) {
            customers.findByUser(user).ifPresent(customers::delete);
            if (providers.findByUser(user).isEmpty()) {
                providers.save(ProviderProfile.builder().user(user)
                        .headline("Provider mới").hourlyRate(BigDecimal.ZERO)
                        .rating(0.0).reviewCount(0).premium(false)
                        .approved(false).profileStatus("ACTIVE")
                        .introduction("").availability("Chưa cập nhật").build());
            }
        } else {
            providers.findByUser(user).ifPresent(providers::delete);
            if (customers.findByUser(user).isEmpty()) {
                customers.save(CustomerProfile.builder().user(user).location("")
                        .preferredSkills("").build());
            }
        }
    }

    /** Compatibility with already-sent, pre-OTP email verification links only. */
    @Transactional
    public boolean verifyEmail(String token) {
        // New OTP hashes cannot be verified using the old link endpoint.
        if (token == null || !token.matches("[0-9a-fA-F\\-]{36}")) return false;
        User user = users.findByEmailVerificationToken(token).orElse(null);
        if (!isPendingVerification(user) || user.getEmailVerificationExpiresAt() == null
                || !user.getEmailVerificationExpiresAt().isAfter(LocalDateTime.now())) return false;
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);
        user.setEmailVerificationAttempts(0);
        user.setEmailVerificationLastSentAt(null);
        users.save(user);
        return true;
    }

    private boolean isPendingVerification(User user) {
        return user != null && user.getAccountStatus() == AccountStatus.PENDING_VERIFICATION
                && !Boolean.TRUE.equals(user.getEmailVerified());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private String newOtp() {
        return String.format(Locale.ROOT, "%06d", OTP_RANDOM.nextInt(1_000_000));
    }

    private String otpEmailBody(String fullName, String otp) {
        return "Xin chào " + fullName + ",\n\nMã OTP xác minh đăng ký SkillShare của bạn: " + otp
                + "\nMã chỉ có hiệu lực trong 5 phút và được nhập tối đa 5 lần sai."
                + "\nKhông chia sẻ mã này với người khác."
                + "\n\nNếu bạn không đăng ký SkillShare, hãy bỏ qua email này.";
    }

    @Transactional
    public void requestPasswordReset(String email) {
        User user = users.findByEmailIgnoreCase(normalizeEmail(email)).orElse(null);
        // Do not disclose whether the submitted email is registered.
        if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE
                || !Boolean.TRUE.equals(user.getEmailVerified())) return;

        if (mailUsername == null || mailUsername.isBlank()
                || mailPassword == null || mailPassword.isBlank()) {
            throw new PasswordResetEmailDeliveryException(
                    "Chưa cấu hình email gửi thư. Hãy thiết lập SKILLSHARE_MAIL_USERNAME và SKILLSHARE_MAIL_PASSWORD.");
        }

        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(30));
        users.save(user);
        try {
            sendMimeMail(user.getEmail(), "Khôi phục mật khẩu SkillShare",
                    "Bạn vừa yêu cầu đặt lại mật khẩu. Liên kết có hiệu lực trong 30 phút:\n"
                            + baseUrl + "/forgot-password?token=" + token
                            + "\n\nNếu không phải bạn yêu cầu, hãy bỏ qua email này.");
        } catch (MailException | MessagingException ex) {
            // Throwing a runtime exception rolls back the reset token change in the same transaction.
            log.warn("Password reset email was not accepted by SMTP ({})", ex.getClass().getSimpleName());
            throw new PasswordResetEmailDeliveryException(
                    "Không gửi được email khôi phục. Hãy kiểm tra SMTP/Gmail App Password và thử lại.");
        }
    }

    @Transactional
    public void reset(String token, String password, String confirmation) {
        if (token == null || !token.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
        }
        if (password == null || password.length() < 8 || password.length() > 72)
            throw new IllegalArgumentException("Mật khẩu phải có từ 8 đến 72 ký tự.");
        if (!password.equals(confirmation))
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        User user = users.findByResetToken(token).orElseThrow(() -> new IllegalArgumentException(
                "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."));
        if (user.getAccountStatus() != AccountStatus.ACTIVE || !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Tài khoản không ở trạng thái cho phép đặt lại mật khẩu.");
        }
        if (user.getResetTokenExpiresAt() == null || !user.getResetTokenExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Liên kết đặt lại mật khẩu đã hết hạn. Hãy yêu cầu liên kết mới.");
        }
        user.setPassword(encoder.encode(password));
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        users.save(user);
    }

    private void sendRegistrationOtpMail(String recipient, String subject, String body) {
        if (mailUsername == null || mailUsername.isBlank()
                || mailPassword == null || mailPassword.isBlank()) {
            throw new OtpEmailDeliveryException(
                    "Chưa cấu hình SMTP. Hãy kiểm tra SKILLSHARE_MAIL_USERNAME và SKILLSHARE_MAIL_PASSWORD.");
        }
        try {
            sendMimeMail(recipient, subject, body);
        } catch (MailException | MessagingException ex) {
            log.warn("Registration OTP email was not accepted by SMTP ({})", ex.getClass().getSimpleName());
            throw new OtpEmailDeliveryException(
                    "SMTP không chấp nhận email OTP. Hãy kiểm tra cấu hình Gmail/App Password rồi thử lại.");
        }
    }

    private void sendMimeMail(String recipient, String subject, String body)
            throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setTo(recipient);
        helper.setSubject(subject);
        helper.setText(body, false);
        helper.setFrom(mailUsername);
        mailSender.send(message);
    }
}
