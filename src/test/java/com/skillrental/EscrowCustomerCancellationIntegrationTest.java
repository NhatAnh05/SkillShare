package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.service.EscrowService;
import com.skillrental.service.EscrowReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 7 - Customer cancellation, evidence-based compensation, and fund-preserving disputes. */
@SpringBootTest
class EscrowCustomerCancellationIntegrationTest {
    static final BigDecimal PRICE = new BigDecimal("900000.00");
    static final BigDecimal INITIAL_AVAILABLE = new BigDecimal("100000.00");
    static final String SUMMARY = "Đã hoàn thành bản thiết kế và bàn giao bản nháp đầu tiên.";
    static final String PROOF = "Bản nháp số 1, tiến độ 30%, link đính kèm trong hội thoại.";
    @Autowired EscrowService escrow;
    @Autowired RentalOrderRepository orders;
    @Autowired UserRepository users;
    @Autowired UserWalletRepository wallets;
    @Autowired WalletTransactionRepository walletTransactions;
    @Autowired EscrowSettlementRepository settlements;
    @Autowired EscrowReconciliationService reconciliation;

    record Scenario(User customer, User provider, RentalOrder order) {}

    User newUser(Role role) {
        return users.saveAndFlush(User.builder().fullName("Cancel phase7 " + role)
                .email(UUID.randomUUID() + "@phase7.test").password("unused")
                .role(role).emailVerified(true).accountStatus(AccountStatus.ACTIVE).build());
    }

    Scenario make(OrderStatus status, boolean started) {
        User customer = newUser(Role.CUSTOMER), provider = newUser(Role.PROVIDER);
        wallets.saveAndFlush(UserWallet.builder().user(customer)
                .balance(INITIAL_AVAILABLE).lockedAmount(PRICE).build());
        wallets.saveAndFlush(UserWallet.builder().user(provider)
                .balance(new BigDecimal("120000.00")).lockedAmount(BigDecimal.ZERO).build());
        RentalOrder.RentalOrderBuilder b = RentalOrder.builder()
                .customer(customer).provider(provider).status(status)
                .subtotal(PRICE).discount(BigDecimal.ZERO).total(PRICE).lockedAmount(PRICE)
                .providerAccepted(status != OrderStatus.PENDING_PROVIDER_ACCEPTANCE)
                .customerStarted(started).providerStarted(started)
                .createdAt(LocalDateTime.now());
        if (started) {
            b.workStartedAt(LocalDateTime.now().minusDays(1))
                    .startDepositAmount(new BigDecimal("300000.00"))
                    .startDepositState(StartDepositState.RESERVED)
                    .startDepositReservedAt(LocalDateTime.now().minusDays(1));
        }
        return new Scenario(customer, provider, orders.saveAndFlush(b.build()));
    }

    void assertEscrowUntouched(Scenario s) {
        assertEquals(PRICE, wallets.findByUser(s.customer()).orElseThrow().getLockedAmount());
        assertEquals(INITIAL_AVAILABLE, wallets.findByUser(s.customer()).orElseThrow().getBalance());
        assertEquals(new BigDecimal("120000.00"), wallets.findByUser(s.provider()).orElseThrow().getBalance());
        assertTrue(settlements.findByOrderId(s.order().getId()).isEmpty());
    }

    @Test void pendingProviderAcceptanceRefundsAllEscrowAndCannotRepeat() {
        Scenario s = make(OrderStatus.PENDING_PROVIDER_ACCEPTANCE, false);
        escrow.cancelByCustomer(s.order().getId(), s.customer(), "Không còn cần dịch vụ");
        RentalOrder o = orders.findById(s.order().getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, o.getStatus());
        assertEquals(0, o.getLockedAmount().signum());
        assertEquals("Không còn cần dịch vụ", o.getCancellationCustomerReason());
        assertEquals(new BigDecimal("1000000.00"), wallets.findByUser(s.customer()).orElseThrow().getBalance());
        assertEquals(0, wallets.findByUser(s.customer()).orElseThrow().getLockedAmount().signum());
        assertEquals(1, walletTransactions.findByOrderIdOrderByIdAsc(o.getId()).stream()
                .filter(t -> "UNHOLD_REFUND".equals(t.getType())).count());
        assertThrows(IllegalStateException.class,
                () -> escrow.cancelByCustomer(o.getId(), s.customer(), "Nhấn lần nữa"));
        assertEquals(new BigDecimal("1000000.00"), wallets.findByUser(s.customer()).orElseThrow().getBalance());
    }

    @Test void oneSideStartedStillRefundsAllBecauseBothHaveNotConfirmed() {
        Scenario s = make(OrderStatus.PENDING_START_CONFIRMATION, false);
        RentalOrder o = s.order();
        o.setCustomerStarted(true);
        o.setCustomerStartConfirmedAt(LocalDateTime.now());
        orders.saveAndFlush(o);
        escrow.cancelByCustomer(o.getId(), s.customer(), "Thay đổi lịch làm việc");
        assertEquals(OrderStatus.CANCELLED, orders.findById(o.getId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("1000000.00"), wallets.findByUser(s.customer()).orElseThrow().getBalance());
        assertNull(orders.findById(o.getId()).orElseThrow().getStartDepositAmount());
    }

    @Test void afterBothStartedCancellationHoldsEscrowAndRequestsProviderProof() {
        Scenario s = make(OrderStatus.PROCESSING, true);
        escrow.cancelByCustomer(s.order().getId(), s.customer(), "Hủy vì kế hoạch thay đổi");
        RentalOrder o = orders.findById(s.order().getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER, o.getStatus());
        assertNotNull(o.getCancellationStartedAt());
        assertEscrowUntouched(s);
        assertThrows(IllegalStateException.class,
                () -> escrow.cancelByCustomer(o.getId(), s.customer(), "Hủy lại"));
    }

    @Test void onlyOwnProviderCanProposeWithValidSummaryAndEvidence() {
        Scenario s = make(OrderStatus.PROCESSING, true);
        escrow.cancelByCustomer(s.order().getId(), s.customer(), "Hủy sau bắt đầu");
        User unrelated = newUser(Role.PROVIDER);
        assertThrows(IllegalArgumentException.class, () -> escrow.proposeCancellationCompensation(
                s.order().getId(), unrelated, new BigDecimal("270000"), SUMMARY, PROOF));
        assertThrows(IllegalArgumentException.class, () -> escrow.proposeCancellationCompensation(
                s.order().getId(), s.provider(), new BigDecimal("900001"), SUMMARY, PROOF));
        assertThrows(IllegalArgumentException.class, () -> escrow.proposeCancellationCompensation(
                s.order().getId(), s.provider(), new BigDecimal("270000"), "Quá ngắn", PROOF));
        assertThrows(IllegalArgumentException.class, () -> escrow.proposeCancellationCompensation(
                s.order().getId(), s.provider(), new BigDecimal("270000"), SUMMARY, null));
        assertEquals(OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER,
                orders.findById(s.order().getId()).orElseThrow().getStatus());
        assertEscrowUntouched(s);
    }

    @Test void agreementSettlesOnceAndRefundsRemainingEscrow() {
        Scenario s = make(OrderStatus.PROCESSING, true);
        Long id = s.order().getId();
        escrow.cancelByCustomer(id, s.customer(), "Khách muốn dừng công việc");
        escrow.proposeCancellationCompensation(id, s.provider(), new BigDecimal("270000.00"), SUMMARY, PROOF);
        assertEscrowUntouched(s);
        RentalOrder proposed = orders.findById(id).orElseThrow();
        assertEquals(OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER, proposed.getStatus());
        assertEquals(PROOF, proposed.getCancellationEvidence());
        assertNotNull(proposed.getCancellationProposedAt());
        escrow.customerResolveCompensation(id, s.customer(), true);
        RentalOrder finished = orders.findById(id).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, finished.getStatus());
        assertEquals(StartDepositState.CLOSED, finished.getStartDepositState());
        assertEquals(0, finished.getLockedAmount().signum());
        assertEquals(new BigDecimal("730000.00"), wallets.findByUser(s.customer()).orElseThrow().getBalance());
        assertEquals(new BigDecimal("376500.00"), wallets.findByUser(s.provider()).orElseThrow().getBalance());
        var ledger = settlements.findByOrderId(id).orElseThrow();
        assertEquals("CUSTOMER_CANCELLATION", ledger.getKind());
        assertEquals(new BigDecimal("630000.00"), ledger.getCustomerRefundAmount());
        assertTrue(reconciliation.reconcile(ledger));
        assertThrows(IllegalStateException.class,
                () -> escrow.customerResolveCompensation(id, s.customer(), true));
        assertEquals(1, settlements.findAll().stream().filter(e -> e.getOrder().getId().equals(id)).count());
    }

    @Test void rejectingProposalFreezesAllFundsWithoutReturningToProcessing() {
        Scenario s = make(OrderStatus.PENDING_CUSTOMER_ACCEPTANCE, true);
        Long id = s.order().getId();
        escrow.cancelByCustomer(id, s.customer(), "Không thống nhất tiến độ");
        escrow.proposeCancellationCompensation(id, s.provider(), new BigDecimal("270000.00"), SUMMARY, PROOF);
        assertThrows(IllegalArgumentException.class,
                () -> escrow.customerResolveCompensation(id, s.customer(), false, "ngắn"));
        escrow.customerResolveCompensation(id, s.customer(), false, "Chi phí chưa phù hợp với kết quả đã bàn giao");
        RentalOrder disputed = orders.findById(id).orElseThrow();
        assertEquals(OrderStatus.CANCELLATION_DISPUTED, disputed.getStatus());
        assertNotNull(disputed.getCancellationDisputedAt());
        assertNotNull(disputed.getCancellationDisputeReason());
        assertEscrowUntouched(s);
        assertThrows(IllegalStateException.class,
                () -> escrow.customerResolveCompensation(id, s.customer(), true));
    }

    @Test void unrelatedCustomerCannotCancelOrder() {
        Scenario s = make(OrderStatus.PROCESSING, true);
        User other = newUser(Role.CUSTOMER);
        assertThrows(IllegalArgumentException.class,
                () -> escrow.cancelByCustomer(s.order().getId(), other, "Không phải của tôi"));
        assertEscrowUntouched(s);
    }

    @Test void corruptedHoldIsRejectedBeforeAnyRefund() {
        Scenario s = make(OrderStatus.PENDING_PROVIDER_ACCEPTANCE, false);
        RentalOrder order = orders.findById(s.order().getId()).orElseThrow();
        order.setLockedAmount(new BigDecimal("800000.00"));
        orders.saveAndFlush(order);
        assertThrows(IllegalStateException.class,
                () -> escrow.cancelByCustomer(order.getId(), s.customer(), "Không đồng ý"));
        assertEscrowUntouched(s);
    }
}
