package com.skillrental.service;

import com.skillrental.entity.PaymentOtp;
import com.skillrental.entity.RentalOrder;
import com.skillrental.entity.User;
import com.skillrental.repository.PaymentOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service @RequiredArgsConstructor
public class OtpService {
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Maximum wrong attempts before the OTP is invalidated and a new one must be requested. */
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private final PaymentOtpRepository otps;
    private final PasswordEncoder passwordEncoder;
    private final MailService mail;

    @Value("${app.otp.expiry-minutes:5}")
    private long expiryMinutes;

    @Value("${app.otp.expose:true}")
    private boolean exposeCode;

    @Transactional
    public String issue(RentalOrder order, User user, String purpose) {
        otps.findByOrderAndConsumedFalse(order).forEach(existing -> {
            existing.setConsumed(true);
            otps.save(existing);
        });

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        PaymentOtp otp = otps.save(PaymentOtp.builder()
                .order(order)
                .user(user)
                .codeHash(passwordEncoder.encode(code))
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusMinutes(Math.max(1, expiryMinutes)))
                .consumed(false)
                .attempts(0)
                .build());

        mail.sendOtp(user.getEmail(), code, purpose);
        return exposeCode ? code : null;
    }

    // REQUIRES_NEW: the attempt counter must be committed even when the caller (finalizePayment)
    // rolls back after a wrong code, otherwise the OTP can never be locked out.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verify(RentalOrder order, User user, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) return false;
        Optional<PaymentOtp> found = otps
                .findFirstByOrderAndUserAndConsumedFalseAndExpiresAtAfterOrderByIdDesc(
                        order, user, LocalDateTime.now());
        if (found.isEmpty()) return false;

        PaymentOtp otp = found.get();
        if (otp.getAttempts() != null && otp.getAttempts() >= MAX_VERIFY_ATTEMPTS) {
            return false; // Blocked after too many wrong attempts; request a new OTP instead.
        }
        otp.setAttempts((otp.getAttempts() == null ? 0 : otp.getAttempts()) + 1);
        if (!passwordEncoder.matches(rawCode.trim(), otp.getCodeHash())) {
            if (otp.getAttempts() >= MAX_VERIFY_ATTEMPTS) {
                otp.setConsumed(true); // Invalidate so the code cannot be brute-forced.
            }
            otps.save(otp);
            return false;
        }
        otp.setConsumed(true);
        otps.save(otp);
        return true;
    }

    public boolean exposeCode() { return exposeCode; }
}
