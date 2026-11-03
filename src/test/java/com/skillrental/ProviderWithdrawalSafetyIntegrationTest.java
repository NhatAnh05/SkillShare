package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.service.EscrowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** Withdrawal wallet debit, OTP resends and cancellation must be idempotent. */
@SpringBootTest(properties = "skillshare.escrow.show-demo-otp=true")
class ProviderWithdrawalSafetyIntegrationTest {
    private static final BigDecimal TWO_MILLION = new BigDecimal("2000000.00");
    private static final BigDecimal THREE_MILLION = new BigDecimal("3000000.00");

    @Autowired EscrowService escrow;
    @Autowired UserRepository users;
    @Autowired UserWalletRepository wallets;
    @Autowired ProviderWithdrawalRepository withdrawals;
    @Autowired WalletTransactionRepository transactions;
    @MockBean JavaMailSender mailSender;

    private User newUser(Role role) {
        return users.saveAndFlush(User.builder()
                .email(UUID.randomUUID() + "@withdrawal-security.test")
                .fullName("Withdrawal Security " + role)
                .role(role).password("unused-test-hash")
                .accountStatus(AccountStatus.ACTIVE).emailVerified(true).build());
    }

    private User providerWithFunds() {
        User provider = newUser(Role.PROVIDER);
        wallets.saveAndFlush(UserWallet.builder().user(provider)
                .balance(THREE_MILLION).lockedAmount(BigDecimal.ZERO).build());
        doThrow(new MailSendException("SMTP disabled for this test"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        return provider;
    }

    private EscrowService.WithdrawalOtpDispatchResult request(User provider) {
        var result = escrow.createWithdrawal(provider, TWO_MILLION,
                "Ngân hàng kiểm thử", "0123456789", "Người nhận kiểm thử");
        assertNotNull(result.demoCode(), "Test configuration must use local fallback OTP");
        return result;
    }

    private void assertOneRefund(User provider) {
        UserWallet w = wallets.findByUser(provider).orElseThrow();
        assertEquals(0, w.getBalance().compareTo(THREE_MILLION));
        assertEquals(1, transactions.findByWalletOrderByCreatedAtDesc(w).stream()
                .filter(tx -> "WITHDRAWAL_RETURN".equals(tx.getType())).count());
    }

    @Test void cancellationCanOnlyRestoreReservedFundsOnce() {
        User provider = providerWithFunds();
        Long id = request(provider).withdrawalId();
        escrow.cancelUnverifiedWithdrawal(id, provider);
        assertThrows(IllegalStateException.class, () -> escrow.cancelUnverifiedWithdrawal(id, provider));
        assertEquals("REJECTED", withdrawals.findById(id).orElseThrow().getStatus());
        assertOneRefund(provider);
    }

    @Test void adminRejectionCanOnlyRestoreVerifiedWithdrawalOnce() {
        User provider = providerWithFunds();
        var request = request(provider);
        escrow.verifyWithdrawalOtp(request.withdrawalId(), provider, request.demoCode());
        User admin = newUser(Role.ADMIN);
        escrow.rejectWithdrawal(request.withdrawalId(), admin, "Không đáp ứng điều kiện");
        assertThrows(IllegalStateException.class,
                () -> escrow.rejectWithdrawal(request.withdrawalId(), admin, "Từ chối trùng"));
        assertOneRefund(provider);
    }

    @Test void resendRequiresCooldownAndHasLifetimeLimit() {
        User provider = providerWithFunds();
        var request = request(provider);
        Long id = request.withdrawalId();
        assertThrows(IllegalStateException.class, () -> escrow.resendWithdrawalOtp(id, provider));
        for (int i = 1; i <= 3; i++) {
            var row = withdrawals.findById(id).orElseThrow();
            row.setOtpLastSentAt(LocalDateTime.now().minusSeconds(61));
            withdrawals.saveAndFlush(row);
            var resent = escrow.resendWithdrawalOtp(id, provider);
            assertNotNull(resent.demoCode());
            assertEquals(i, withdrawals.findById(id).orElseThrow().getOtpResendCount());
        }
        var row = withdrawals.findById(id).orElseThrow();
        row.setOtpLastSentAt(LocalDateTime.now().minusSeconds(61));
        withdrawals.saveAndFlush(row);
        assertThrows(IllegalStateException.class, () -> escrow.resendWithdrawalOtp(id, provider));
        assertEquals(3, withdrawals.findById(id).orElseThrow().getOtpResendCount());
        assertEquals(0, wallets.findByUser(provider).orElseThrow().getBalance()
                .compareTo(new BigDecimal("1000000.00")));
    }

    @Test void subCentAmountIsRejectedBeforeWalletDebit() {
        User provider = providerWithFunds();
        assertThrows(IllegalArgumentException.class,
                () -> escrow.createWithdrawal(provider, new BigDecimal("2000000.001"),
                        "Ngân hàng kiểm thử", "0123456789", "Người nhận kiểm thử"));
        assertEquals(0, wallets.findByUser(provider).orElseThrow().getBalance().compareTo(THREE_MILLION));
        assertTrue(withdrawals.findByProviderOrderByCreatedAtDesc(provider).isEmpty());
    }

    @Test void concurrentCancellationDoesNotDuplicateRefund() throws Exception {
        User provider = providerWithFunds();
        Long id = request(provider).withdrawalId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> cancel = () -> {
                start.await();
                try {
                    escrow.cancelUnverifiedWithdrawal(id, provider);
                    return true;
                } catch (IllegalStateException | org.springframework.dao.PessimisticLockingFailureException ex) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(cancel);
            Future<Boolean> second = executor.submit(cancel);
            start.countDown();
            int successful = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successful);
            assertOneRefund(provider);
        } finally {
            executor.shutdownNow();
        }
    }
}
