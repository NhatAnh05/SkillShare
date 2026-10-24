package com.skillrental.service;

import com.skillrental.dto.RegisterRequest;
import com.skillrental.entity.*;
import com.skillrental.repository.*;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final CustomerProfileRepository customers;
    private final PasswordEncoder encoder;
    private final JavaMailSender mailSender;
    @Value("${app.base-url:http://localhost:8080}") private String baseUrl;
    @Value("${spring.mail.username:}") private String mailUsername;

    @Transactional
    public User register(RegisterRequest form) {
        if (form.getPassword() == null || form.getPassword().length() < 8)
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 8 ký tự.");
        if (!form.getPassword().equals(form.getConfirmPassword()))
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        if (users.existsByEmailIgnoreCase(form.getEmail()))
            throw new IllegalArgumentException("Email đã được sử dụng.");
        LocalDateTime now = LocalDateTime.now();
        String token = UUID.randomUUID().toString();
        User user = users.save(User.builder()
                .fullName(form.getFullName().strip())
                .email(form.getEmail().strip().toLowerCase())
                .password(encoder.encode(form.getPassword()))
                .role(Role.CUSTOMER)
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .emailVerified(false)
                .emailVerificationToken(token)
                .emailVerificationExpiresAt(now.plusHours(24))
                .avatarUrl("https://i.pravatar.cc/150?img=33")
                .bio("Customer của Skill Rental")
                .build());
        customers.save(CustomerProfile.builder().user(user).location("TP. Hồ Chí Minh")
                .preferredSkills("Java, UI/UX, Marketing").build());
        sendMail(user.getEmail(), "Xác nhận email SkillShare",
                "Xin chào " + user.getFullName() + ",\n\nVui lòng xác nhận email trong 24 giờ bằng liên kết sau:\n"
                        + baseUrl + "/verify-email?token=" + token + "\n\nNếu bạn không đăng ký SkillShare, hãy bỏ qua email này.");
        return user;
    }

    @Transactional
    public boolean verifyEmail(String token) {
        if (token == null || token.isBlank()) return false;
        User user = users.findByEmailVerificationToken(token).orElse(null);
        if (user == null || user.getEmailVerificationExpiresAt() == null
                || user.getEmailVerificationExpiresAt().isBefore(LocalDateTime.now())) return false;
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);
        users.save(user);
        return true;
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

    private boolean sendMail(String recipient, String subject, String body) {
        if (mailUsername == null || mailUsername.isBlank()) return false;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body, false);
            if (!mailUsername.isBlank()) helper.setFrom(mailUsername);
            mailSender.send(message);
            return true;
        } catch (MailException | MessagingException ex) {
            return false;
        }
    }
}
