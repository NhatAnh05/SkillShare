package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.service.EscrowService;
import com.skillrental.service.EscrowReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 6: One and only one verified settlement transfers value from escrow to Provider. */
@SpringBootTest
class EscrowSettlementLedgerIntegrationTest {
    private static final BigDecimal PRICE = new BigDecimal("900000.00");
    @Autowired private EscrowService escrow;
    @Autowired private UserRepository users;
    @Autowired private UserWalletRepository wallets;
    @Autowired private RentalOrderRepository orders;
    @Autowired private DeliverySubmissionRepository deliveries;
    @Autowired private EscrowSettlementRepository settlements;
    @Autowired private WalletTransactionRepository transactions;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EscrowReconciliationService reconciliation;

    private record Scenario(User customer, User provider, RentalOrder order) {}

    private User newUser(Role role) {
        return users.saveAndFlush(User.builder()
                .fullName("Phase6 test " + role)
                .email(UUID.randomUUID() + "@phase6.test")
                .password("unused-password-hash")
                .role(role).accountStatus(AccountStatus.ACTIVE).emailVerified(true).build());
    }

    private Scenario create(OrderStatus status) {
        User customer = newUser(Role.CUSTOMER);
        User provider = newUser(Role.PROVIDER);
        wallets.saveAndFlush(UserWallet.builder().user(customer)
                .balance(new BigDecimal("100000.00")).lockedAmount(PRICE).build());
        wallets.saveAndFlush(UserWallet.builder().user(provider)
                .balance(new BigDecimal("120000.00")).lockedAmount(BigDecimal.ZERO).build());
        RentalOrder.RentalOrderBuilder builder = RentalOrder.builder()
                .customer(customer).provider(provider)
                .subtotal(PRICE).discount(BigDecimal.ZERO).total(PRICE)
                .lockedAmount(PRICE).status(status)
                .providerAccepted(true).customerStarted(true).providerStarted(true)
                .workStartedAt(LocalDateTime.now().minusDays(1))
                .startDepositAmount(new BigDecimal("300000.00"))
                .startDepositState(StartDepositState.RESERVED)
                .startDepositReservedAt(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now());
        if (status == OrderStatus.PENDING_PAYMENT_OTP) {
            builder.providerCompleted(true).customerAcceptedCompletion(true)
                    .completionOtpHash(passwordEncoder.encode("784512"))
                    .completionOtpExpiresAt(LocalDateTime.now().plusMinutes(10));
        } else if (status == OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER) {
            builder.cancellationCompensation(new BigDecimal("270000.00"));
        }
        RentalOrder order = orders.saveAndFlush(builder.build());
        if (status == OrderStatus.PENDING_PAYMENT_OTP) {
            deliveries.saveAndFlush(DeliverySubmission.builder()
                    .order(order).provider(provider).version(1)
                    .description("Đã bàn giao và được Customer nghiệm thu")
                    .status("ACCEPTED").build());
        }
        return new Scenario(customer, provider, order);
    }

    @Test void validOtpSettlesOnceWithBalancedLedgerAndNoSecondThirdDebit() {
        Scenario s = create(OrderStatus.PENDING_PAYMENT_OTP);
        Long id = s.order().getId();
        escrow.verifyOtpAndSettle(id, s.customer(), "784512");
        RentalOrder order = orders.findById(id).orElseThrow();
        EscrowSettlement ledger = settlements.findByOrderId(id).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(StartDepositState.CLOSED, order.getStartDepositState());
        assertEquals(0, order.getLockedAmount().signum());
        assertNull(order.getCompletionOtpHash());
        assertEquals(new BigDecimal("45000.00"), ledger.getPlatformFeeAmount());
        assertEquals(new BigDecimal("855000.00"), ledger.getProviderNetAmount());
        assertEquals(new BigDecimal("900000.00"), ledger.getHeldBefore());
        assertTrue(ledger.isBalanced());
        assertTrue(reconciliation.reconcile(ledger), "Ledger phải khớp với giao dịch ví của hai bên.");
        assertEquals("COMPLETION", ledger.getKind());
        assertEquals(new BigDecimal("100000.00"), wallets.findByUser(s.customer()).orElseThrow().getBalance());
        assertEquals(0, wallets.findByUser(s.customer()).orElseThrow().getLockedAmount().signum());
        assertEquals(new BigDecimal("975000.00"), wallets.findByUser(s.provider()).orElseThrow().getBalance());
        assertEquals(1L, transactions.findByWalletOrderByCreatedAtDesc(
                wallets.findByUser(s.provider()).orElseThrow()).stream()
                .filter(t -> "SETTLEMENT_IN".equals(t.getType()) && t.getOrder().getId().equals(id)).count());

        assertThrows(IllegalStateException.class,
                () -> escrow.verifyOtpAndSettle(id, s.customer(), "784512"));
        assertEquals(1L, settlements.findAll().stream()
                .filter(e -> e.getOrder().getId().equals(id)).count());
        assertEquals(new BigDecimal("975000.00"), wallets.findByUser(s.provider()).orElseThrow().getBalance());
    }

    @Test void insufficientCustomerEscrowRejectsSettlementWithoutCreditingProvider() {
        Scenario s = create(OrderStatus.PENDING_PAYMENT_OTP);
        UserWallet customerWallet = wallets.findByUser(s.customer()).orElseThrow();
        customerWallet.setLockedAmount(new BigDecimal("200000.00"));
        wallets.saveAndFlush(customerWallet);
        assertThrows(IllegalStateException.class,
                () -> escrow.verifyOtpAndSettle(s.order().getId(), s.customer(), "784512"));
        assertTrue(settlements.findByOrderId(s.order().getId()).isEmpty());
        assertEquals(new BigDecimal("120000.00"), wallets.findByUser(s.provider()).orElseThrow().getBalance());
        assertEquals(OrderStatus.PENDING_PAYMENT_OTP, orders.findById(s.order().getId()).orElseThrow().getStatus());
    }

    @Test void cancellationAgreementSettlesOnlyCompensationAndReturnsRemainingEscrow() {
        Scenario s = create(OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER);
        escrow.customerResolveCompensation(s.order().getId(), s.customer(), true);
        var ledger = settlements.findByOrderId(s.order().getId()).orElseThrow();
        assertEquals("CUSTOMER_CANCELLATION", ledger.getKind());
        assertTrue(ledger.isBalanced());
        assertTrue(reconciliation.reconcile(ledger));
        assertEquals(new BigDecimal("630000.00"), ledger.getCustomerRefundAmount());
        assertEquals(new BigDecimal("13500.00"), ledger.getPlatformFeeAmount());
        assertEquals(new BigDecimal("256500.00"), ledger.getProviderNetAmount());
        assertEquals(OrderStatus.CANCELLED, orders.findById(s.order().getId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("730000.00"), wallets.findByUser(s.customer()).orElseThrow().getBalance());
        assertEquals(new BigDecimal("376500.00"), wallets.findByUser(s.provider()).orElseThrow().getBalance());
        assertThrows(IllegalStateException.class,
                () -> escrow.customerResolveCompensation(s.order().getId(), s.customer(), true));
        assertEquals(1L, settlements.findAll().stream()
                .filter(e -> e.getOrder().getId().equals(s.order().getId())).count());
    }

    @Test void wrongOtpCannotChangeAnyWalletOrCreateSettlement() {
        Scenario s = create(OrderStatus.PENDING_PAYMENT_OTP);
        assertThrows(IllegalArgumentException.class,
                () -> escrow.verifyOtpAndSettle(s.order().getId(), s.customer(), "000000"));
        assertTrue(settlements.findByOrderId(s.order().getId()).isEmpty());
        assertEquals(new BigDecimal("900000.00"), wallets.findByUser(s.customer()).orElseThrow().getLockedAmount());
        assertEquals(new BigDecimal("120000.00"), wallets.findByUser(s.provider()).orElseThrow().getBalance());
        assertEquals(1, orders.findById(s.order().getId()).orElseThrow().getCompletionOtpAttempts());
    }
    @Test void missingProviderTransactionIsFlaggedForAdminReconciliation() {
        Scenario s = create(OrderStatus.PENDING_PAYMENT_OTP);
        escrow.verifyOtpAndSettle(s.order().getId(), s.customer(), "784512");
        EscrowSettlement ledger = settlements.findByOrderId(s.order().getId()).orElseThrow();
        assertTrue(reconciliation.reconcile(ledger));
        WalletTransaction providerCredit = transactions.findByOrderIdOrderByIdAsc(s.order().getId()).stream()
                .filter(t -> "SETTLEMENT_IN".equals(t.getType())).findFirst().orElseThrow();
        transactions.deleteById(providerCredit.getId());
        assertFalse(reconciliation.reconcile(ledger), "Mất bút toán Provider phải được Admin nhận diện.");
    }

}
