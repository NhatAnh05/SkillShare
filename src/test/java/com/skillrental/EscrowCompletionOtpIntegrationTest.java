package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.service.DeliveryService;
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
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Phase 5: final payment is impossible without accepted delivery and a valid email OTP. */
@SpringBootTest(properties = {"skillshare.delivery.storage-dir=./target/phase5-deliveries",
        "spring.mail.username=phase5-notify@skillshare.test"})
class EscrowCompletionOtpIntegrationTest {
    private static final BigDecimal PRICE = new BigDecimal("900000.00");
    private static final Pattern MAIL_CODE = Pattern.compile("\\b([0-9]{6})\\b");

    @Autowired private UserRepository users;
    @Autowired private RentalOrderRepository orders;
    @Autowired private UserWalletRepository wallets;
    @Autowired private DeliverySubmissionRepository submissions;
    @Autowired private DeliveryService deliveryService;
    @Autowired private EscrowService escrow;
    @MockBean private JavaMailSender mailSender;

    private record Fixture(User customer, User provider, RentalOrder order) {}

    private User user(Role role) {
        return users.saveAndFlush(User.builder()
                .fullName("Phase5 " + role)
                .email(UUID.randomUUID() + "@phase5.test")
                .password("unused-test-hash")
                .role(role)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build());
    }

    private Fixture prepareStartedOrder() {
        User customer = user(Role.CUSTOMER);
        User provider = user(Role.PROVIDER);
        wallets.saveAndFlush(UserWallet.builder().user(customer)
                .balance(BigDecimal.ZERO).lockedAmount(PRICE).build());
        wallets.saveAndFlush(UserWallet.builder().user(provider)
                .balance(BigDecimal.ZERO).lockedAmount(BigDecimal.ZERO).build());
        RentalOrder order = orders.saveAndFlush(RentalOrder.builder()
                .customer(customer).provider(provider)
                .subtotal(PRICE).discount(BigDecimal.ZERO).total(PRICE)
                .lockedAmount(PRICE)
                .status(OrderStatus.PROCESSING)
                .customerStarted(true).providerStarted(true).providerAccepted(true)
                .createdAt(LocalDateTime.now()).build());
        return new Fixture(customer, provider, order);
    }

    private Long submit(Fixture f) {
        deliveryService.submit(f.order().getId(), f.provider(), "Bàn giao kết quả thật", "Xem liên kết",
                List.of("https://example.org/product"), List.of());
        assertEquals(OrderStatus.PENDING_CUSTOMER_ACCEPTANCE,
                orders.findById(f.order().getId()).orElseThrow().getStatus());
        return f.order().getId();
    }

    private String lastSentCode() {
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        String message = captor.getAllValues().get(captor.getAllValues().size() - 1).getText();
        assertNotNull(message);
        Matcher matcher = MAIL_CODE.matcher(message);
        assertTrue(matcher.find(), "Thông báo email phải chứa OTP sáu chữ số.");
        return matcher.group(1);
    }

    @Test
    void providerDeliveryDoesNotPayUntilCustomerAcceptsAndVerifiesOtp() {
        Fixture f = prepareStartedOrder();
        Long id = submit(f);
        assertEquals(BigDecimal.ZERO, wallets.findByUser(f.provider()).orElseThrow().getBalance());
        assertEquals(PRICE, wallets.findByUser(f.customer()).orElseThrow().getLockedAmount());

        var result = deliveryService.acceptLatest(id, f.customer());
        assertTrue(result.emailSent());
        assertNull(result.demoCode());
        assertEquals(OrderStatus.PENDING_PAYMENT_OTP, orders.findById(id).orElseThrow().getStatus());
        assertEquals("ACCEPTED", submissions.findFirstByOrderIdOrderByVersionDesc(id).orElseThrow().getStatus());
        assertEquals(BigDecimal.ZERO, wallets.findByUser(f.provider()).orElseThrow().getBalance());
        assertEquals(PRICE, wallets.findByUser(f.customer()).orElseThrow().getLockedAmount());

        escrow.verifyOtpAndSettle(id, f.customer(), lastSentCode());
        var paid = orders.findById(id).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, paid.getStatus());
        assertNull(paid.getCompletionOtpHash());
        assertNull(paid.getCompletionOtpExpiresAt());
        assertEquals(0, paid.getLockedAmount().signum());
        assertEquals(0, wallets.findByUser(f.customer()).orElseThrow().getLockedAmount().signum());
        assertEquals(new BigDecimal("855000.00"), wallets.findByUser(f.provider()).orElseThrow().getBalance());
        assertThrows(IllegalStateException.class, () -> escrow.verifyOtpAndSettle(id, f.customer(), "123456"));
        assertEquals(new BigDecimal("855000.00"), wallets.findByUser(f.provider()).orElseThrow().getBalance());
    }

    @Test
    void failedSmtpRollsBackAcceptanceAndKeepsAllEscrowLocked() {
        Fixture f = prepareStartedOrder();
        Long id = submit(f);
        doThrow(new MailSendException("Test transport unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        assertThrows(IllegalStateException.class, () -> deliveryService.acceptLatest(id, f.customer()));
        var after = orders.findById(id).orElseThrow();
        assertEquals(OrderStatus.PENDING_CUSTOMER_ACCEPTANCE, after.getStatus());
        assertFalse(Boolean.TRUE.equals(after.getCustomerAcceptedCompletion()));
        assertNull(after.getCompletionOtpHash());
        assertEquals("SUBMITTED", submissions.findFirstByOrderIdOrderByVersionDesc(id).orElseThrow().getStatus());
        assertEquals(PRICE, wallets.findByUser(f.customer()).orElseThrow().getLockedAmount());
        assertEquals(BigDecimal.ZERO, wallets.findByUser(f.provider()).orElseThrow().getBalance());
    }

    @Test
    void wrongOtpPersistsAttemptsAndCannotSettle() {
        Fixture f = prepareStartedOrder();
        Long id = submit(f);
        deliveryService.acceptLatest(id, f.customer());
        for (int i = 1; i <= 5; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> escrow.verifyOtpAndSettle(id, f.customer(), "wrong-code"));
            assertEquals(i, orders.findById(id).orElseThrow().getCompletionOtpAttempts());
        }
        assertThrows(IllegalStateException.class,
                () -> escrow.verifyOtpAndSettle(id, f.customer(), lastSentCode()));
        assertEquals(PRICE, wallets.findByUser(f.customer()).orElseThrow().getLockedAmount());
        assertEquals(BigDecimal.ZERO, wallets.findByUser(f.provider()).orElseThrow().getBalance());
    }

    @Test
    void resendRequiresCooldownAndInvalidatesPreviousCode() {
        Fixture f = prepareStartedOrder();
        Long id = submit(f);
        deliveryService.acceptLatest(id, f.customer());
        String first = lastSentCode();
        assertThrows(IllegalStateException.class, () -> escrow.resendCompletionOtp(id, f.customer()));
        RentalOrder order = orders.findById(id).orElseThrow();
        order.setCompletionOtpLastSentAt(LocalDateTime.now().minusMinutes(2));
        orders.saveAndFlush(order);
        escrow.resendCompletionOtp(id, f.customer());
        assertEquals(1, orders.findById(id).orElseThrow().getCompletionOtpResendCount());
        assertThrows(IllegalArgumentException.class,
                () -> escrow.verifyOtpAndSettle(id, f.customer(), first));
        // Wrong-code attempt remains counted even after a resend.
        assertEquals(1, orders.findById(id).orElseThrow().getCompletionOtpAttempts());
        assertEquals(PRICE, wallets.findByUser(f.customer()).orElseThrow().getLockedAmount());
    }

    @Test
    void expiredOtpAndUnauthorizedCustomerNeverMoveFunds() {
        Fixture f = prepareStartedOrder();
        Long id = submit(f);
        User outsider = user(Role.CUSTOMER);
        assertThrows(IllegalArgumentException.class,
                () -> deliveryService.acceptLatest(id, outsider));
        deliveryService.acceptLatest(id, f.customer());
        assertThrows(IllegalArgumentException.class,
                () -> escrow.verifyOtpAndSettle(id, outsider, lastSentCode()));
        RentalOrder order = orders.findById(id).orElseThrow();
        order.setCompletionOtpExpiresAt(LocalDateTime.now().minusSeconds(1));
        orders.saveAndFlush(order);
        assertThrows(IllegalStateException.class,
                () -> escrow.verifyOtpAndSettle(id, f.customer(), lastSentCode()));
        assertEquals(OrderStatus.PENDING_PAYMENT_OTP, orders.findById(id).orElseThrow().getStatus());
        assertEquals(PRICE, wallets.findByUser(f.customer()).orElseThrow().getLockedAmount());
        assertEquals(BigDecimal.ZERO, wallets.findByUser(f.provider()).orElseThrow().getBalance());
    }

    @Test
    void revisionCannotBeMistakenForAcceptance() {
        Fixture f = prepareStartedOrder();
        Long id = submit(f);
        Long deliveryId = submissions.findFirstByOrderIdOrderByVersionDesc(id).orElseThrow().getId();
        deliveryService.requestRevision(id, f.customer(), deliveryId, "Cần bổ sung tài liệu");
        assertEquals(OrderStatus.PROCESSING, orders.findById(id).orElseThrow().getStatus());
        assertThrows(IllegalStateException.class, () -> deliveryService.acceptLatest(id, f.customer()));
        assertEquals(BigDecimal.ZERO, wallets.findByUser(f.provider()).orElseThrow().getBalance());
    }

    @Test
    void resendIsCappedAtThreeAndCannotResetFailedAttemptCounter() {
        Fixture f = prepareStartedOrder();
        Long id = submit(f);
        deliveryService.acceptLatest(id, f.customer());
        assertThrows(IllegalArgumentException.class,
                () -> escrow.verifyOtpAndSettle(id, f.customer(), "invalid"));
        for (int count = 1; count <= 3; count++) {
            RentalOrder order = orders.findById(id).orElseThrow();
            order.setCompletionOtpLastSentAt(LocalDateTime.now().minusMinutes(2));
            orders.saveAndFlush(order);
            escrow.resendCompletionOtp(id, f.customer());
            assertEquals(count, orders.findById(id).orElseThrow().getCompletionOtpResendCount());
            assertEquals(1, orders.findById(id).orElseThrow().getCompletionOtpAttempts());
        }
        assertThrows(IllegalStateException.class, () -> escrow.resendCompletionOtp(id, f.customer()));
        assertEquals(PRICE, wallets.findByUser(f.customer()).orElseThrow().getLockedAmount());
    }

    @Test
    void duplicateAcceptanceIsBlockedWithoutSendingSecondEmail() {
        Fixture f = prepareStartedOrder();
        Long id = submit(f);
        deliveryService.acceptLatest(id, f.customer());
        assertThrows(IllegalStateException.class, () -> deliveryService.acceptLatest(id, f.customer()));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertEquals(OrderStatus.PENDING_PAYMENT_OTP, orders.findById(id).orElseThrow().getStatus());
        assertEquals(BigDecimal.ZERO, wallets.findByUser(f.provider()).orElseThrow().getBalance());
    }

    @Test
    void customerCannotAcceptUntilProviderHasDelivered() {
        Fixture f = prepareStartedOrder();
        assertThrows(IllegalStateException.class,
                () -> deliveryService.acceptLatest(f.order().getId(), f.customer()));
        assertThrows(IllegalStateException.class,
                () -> escrow.verifyOtpAndSettle(f.order().getId(), f.customer(), "123456"));
        assertEquals(PRICE, wallets.findByUser(f.customer()).orElseThrow().getLockedAmount());
    }
}
