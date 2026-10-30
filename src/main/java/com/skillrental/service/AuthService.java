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
                               boolean resendCooldownActive, String fullName, LocalDateTime resendAvailableAt) {}

    public static class OtpEmailDeliveryException extends IllegalStateException {
        public OtpEmailDeliveryException(String message) {
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

        if (user == null) {
            user = users.saveAndFlush(User.builder()
                    .fullName(form.getFullName().strip())
                    .email(email)
                    .password(encoder.encode(form.getPassword()))
                    .role(Role.CUSTOMER)
                    .accountStatus(AccountStatus.PENDING_VERIFICATION)
                    .emailVerified(false)
                    .emailVerificationAttempts(0)
                    .avatarUrl("https://i.pravatar.cc/150?img=33")
                    .bio("Customer của Skill Rental")
                    .build());
            customers.save(CustomerProfile.builder().user(user).location("TP. Hồ Chí Minh")
                    .preferredSkills("Java, UI/UX, Marketing").build());
        }

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
            return new OtpPageState(false, false, false, false, null, now);
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
                resendAvailableAt.isAfter(now), user.getFullName(), resendAvailableAt);
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
        if (!form.isAcceptedTerms()) {
            throw new IllegalArgumentException("Bạn phải đồng ý điều khoản.");
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
    public String createResetToken(String email) {
        User user = users.findByEmailIgnoreCase(email == null ? "" : email.strip()).orElse(null);
        if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE) return null;
        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(30));
        users.save(user);
        sendMail(user.getEmail(), "Khôi phục mật khẩu SkillShare",
                "Bạn vừa yêu cầu đặt lại mật khẩu. Liên kết có hiệu lực trong 30 phút:\n"
                        + baseUrl + "/forgot-password?token=" + token + "\n\nNếu không phải bạn yêu cầu, hãy bỏ qua email này.");
        return token;
    }

    @Transactional
    public void reset(String token, String password, String confirmation) {
        if (password == null || password.length() < 8)
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 8 ký tự.");
        if (!password.equals(confirmation))
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        User user = users.findByResetToken(token).orElseThrow(() -> new IllegalArgumentException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."));
        if (user.getResetTokenExpiresAt() == null || user.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            user.setResetToken(null);
            user.setResetTokenExpiresAt(null);
            users.save(user);
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

    private boolean sendMail(String recipient, String subject, String body) {
        if (mailUsername == null || mailUsername.isBlank()
                || mailPassword == null || mailPassword.isBlank()) return false;
        try {
            sendMimeMail(recipient, subject, body);
            return true;
        } catch (MailException | MessagingException ex) {
            log.warn("Email delivery was not accepted by SMTP ({})", ex.getClass().getSimpleName());
            return false;
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
