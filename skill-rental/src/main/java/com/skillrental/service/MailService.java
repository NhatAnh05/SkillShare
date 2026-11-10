package com.skillrental.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional notifications. When SMTP is not configured (spring.mail.host blank)
 * the message is written to the application log so the demo flow still works offline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.username:no-reply@skillrental.vn}")
    private String from;

    public boolean smtpConfigured() {
        return host != null && !host.isBlank();
    }

    public void sendOtp(String to, String code, String purpose) {
        String subject = "SkillShare - Ma OTP xac nhan thanh toan";
        String body = "Xin chao,\n\n"
                + "Ma OTP de xac nhan " + purpose + " cua ban la: " + code + "\n"
                + "Ma co hieu luc trong vai phut. Khong chia se ma nay cho bat ky ai.\n\n"
                + "Neu ban khong thuc hien giao dich, hay bo qua email nay.\n\n"
                + "SkillShare";
        send(to, subject, body);
    }

    public void sendNotification(String to, String subject, String body) {
        send(to, subject, body);
    }

    private void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.warn("Khong gui duoc email vi thieu dia chi nguoi nhan. Subject: {}", subject);
            return;
        }
        if (!smtpConfigured()) {
            log.info("[DEMO MAIL] To={} | Subject={} | Body={}", to, subject, body.replace('\n', ' '));
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            JavaMailSender sender = mailSenderProvider.getIfAvailable();
            if (sender == null) {
                log.info("[DEMO MAIL - no JavaMailSender] To={} | Subject={} | Body={}", to, subject,
                        body.replace('\n', ' '));
                return;
            }
            sender.send(message);
            log.info("Da gui email '{}' toi {}", subject, to);
        } catch (Exception ex) {
            log.warn("Gui email that bai toi {} - fallback log: {}", to, ex.getMessage());
            log.info("[DEMO MAIL FALLBACK] To={} | Subject={} | Body={}", to, subject,
                    body.replace('\n', ' '));
        }
    }
}
