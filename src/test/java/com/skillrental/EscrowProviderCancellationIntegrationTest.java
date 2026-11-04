package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.service.EscrowService;
import com.skillrental.service.EscrowReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 8: Provider cancellation guarantees a full refund BEFORE any OTP settlement. */
@SpringBootTest
class EscrowProviderCancellationIntegrationTest {
    private static final BigDecimal PRICE = new BigDecimal("900000.00");
    private static final BigDecimal CUSTOMER_BEFORE = new BigDecimal("100000.00");
    private static final BigDecimal PROVIDER_BEFORE = new BigDecimal("120000.00");
    @Autowired EscrowService escrow;
    @Autowired UserRepository users;
    @Autowired RentalOrderRepository orders;
    @Autowired UserWalletRepository wallets;
    @Autowired WalletTransactionRepository transactions;
    @Autowired EscrowSettlementRepository settlements;
    @Autowired EscrowReconciliationService reconciliation;
    @Autowired BookingRepository bookings;
    @Autowired ComplaintRepository complaints;

    record Scenario(User customer, User provider, RentalOrder order) {}

    private User user(Role role) {
        return users.saveAndFlush(User.builder().fullName("Phase8 " + role)
                .email(UUID.randomUUID() + "@phase8.test")
                .password("unused-test-hash").role(role)
                .emailVerified(true).accountStatus(AccountStatus.ACTIVE).build());
    }

    private Scenario create(OrderStatus status, boolean withBooking) {
        User customer = user(Role.CUSTOMER), provider = user(Role.PROVIDER);
        wallets.saveAndFlush(UserWallet.builder().user(customer)
                .balance(CUSTOMER_BEFORE).lockedAmount(PRICE).build());
        wallets.saveAndFlush(UserWallet.builder().user(provider)
                .balance(PROVIDER_BEFORE).lockedAmount(BigDecimal.ZERO).build());
        boolean started = status != OrderStatus.PENDING_PROVIDER_ACCEPTANCE
                && status != OrderStatus.PENDING_START_CONFIRMATION;
        Booking booking = null;
        if (withBooking) {
            booking = bookings.saveAndFlush(Booking.builder().customer(customer).provider(provider)
                    .serviceName("Kiểm thử hoàn tiền Provider")
                    .bookingDate(LocalDate.now().plusDays(1)).timeSlot(LocalTime.NOON)
                    .premium(false).directBooking(true).amount(PRICE)
                    .status(started ? BookingStatus.IN_PROGRESS : BookingStatus.PENDING).build());
        }
        RentalOrder.RentalOrderBuilder b = RentalOrder.builder()
                .booking(booking).customer(customer).provider(provider).subtotal(PRICE)
                .discount(BigDecimal.ZERO).total(PRICE).lockedAmount(PRICE).status(status)
                .createdAt(LocalDateTime.now()).providerAccepted(status != OrderStatus.PENDING_PROVIDER_ACCEPTANCE)
                .customerStarted(started).providerStarted(started);
        if (started) b.workStartedAt(LocalDateTime.now().minusHours(2))
                .startDepositAmount(new BigDecimal("300000.00"))
                .startDepositState(StartDepositState.RESERVED)
                .startDepositReservedAt(LocalDateTime.now().minusHours(2));
        if (status == OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER
                || status == OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER
                || status == OrderStatus.CANCELLATION_DISPUTED) {
            b.cancellationReason("Customer đã yêu cầu dừng")
                    .cancellationCustomerReason("Customer đã yêu cầu dừng");
        }
        if (status == OrderStatus.PENDING_PAYMENT_OTP) {
            b.providerCompleted(true).customerAcceptedCompletion(true)
                    .completionOtpHash("hashed-otp-before-provider-cancel")
                    .completionOtpExpiresAt(LocalDateTime.now().plusMinutes(10))
                    .completionOtpResendCount(2).completionOtpAttempts(1);
        }
        if (status == OrderStatus.CANCELLATION_DISPUTED) {
            b.cancellationDisputeReason("Không đồng ý chi phí")
                    .cancellationDisputedAt(LocalDateTime.now());
        }
        return new Scenario(customer, provider, orders.saveAndFlush(b.build()));
    }

    private void assertFullRefund(Scenario s, boolean started) {
        RentalOrder o = orders.findById(s.order().getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, o.getStatus());
        assertEquals(0, o.getLockedAmount().signum());
        assertNotNull(o.getCancelledAt());
        assertEquals(s.provider().getId(), o.getCancelledBy().getId());
        assertNotNull(o.getSettledAt());
        assertEquals(0, o.getPlatformFeeAmount().signum());
        assertEquals(0, o.getProviderNetAmount().signum());
        assertTrue(o.getCancellationReason().contains("Provider hủy"));
        assertEquals(started ? StartDepositState.CLOSED : null, o.getStartDepositState());
        assertEquals(new BigDecimal("1000000.00"), wallets.findByUser(s.customer()).orElseThrow().getBalance());
        assertEquals(0, wallets.findByUser(s.customer()).orElseThrow().getLockedAmount().signum());
        assertEquals(PROVIDER_BEFORE, wallets.findByUser(s.provider()).orElseThrow().getBalance());
        EscrowSettlement audit = settlements.findByOrderId(o.getId()).orElseThrow();
        assertEquals("PROVIDER_CANCELLATION", audit.getKind());
        assertEquals(PRICE, audit.getHeldBefore());
        assertEquals(PRICE, audit.getCustomerRefundAmount());
        assertEquals(0, audit.getGrossAmount().signum());
        assertEquals(0, audit.getPlatformFeeAmount().signum());
        assertEquals(0, audit.getProviderNetAmount().signum());
        assertTrue(audit.isBalanced());
        assertTrue(reconciliation.reconcile(audit));
        var rows = transactions.findByOrderIdOrderByIdAsc(o.getId());
        assertEquals(1, rows.stream().filter(t -> "UNHOLD_REFUND".equals(t.getType())).count());
        assertEquals(0, rows.stream().filter(t -> "SETTLEMENT_IN".equals(t.getType())).count());
        assertEquals(0, rows.stream().filter(t -> "CANCELLATION_REFUND".equals(t.getType())).count());
        assertThrows(IllegalStateException.class, () -> escrow.cancelByProvider(o.getId(), s.provider(), "Hủy lại"));
        assertEquals(new BigDecimal("1000000.00"), wallets.findByUser(s.customer()).orElseThrow().getBalance());
    }

    @Test void providerDeclineBeforeAcceptRefundsAndKeepsBookingConsistent() {
        Scenario s = create(OrderStatus.PENDING_PROVIDER_ACCEPTANCE, true);
        escrow.providerDecline(s.order().getId(), s.provider(), "Không nhận được công việc");
        assertFullRefund(s, false);
        assertEquals(BookingStatus.CANCELLED,
                bookings.findById(s.order().getBooking().getId()).orElseThrow().getStatus());
    }

    @Test void providerCancelsAfterOnlyCustomerAcknowledgedStart() {
        Scenario s = create(OrderStatus.PENDING_START_CONFIRMATION, false);
        RentalOrder o = s.order();
        o.setCustomerStarted(true);
        orders.saveAndFlush(o);
        escrow.cancelByProvider(o.getId(), s.provider(), "Lịch không phù hợp");
        assertFullRefund(s, false);
    }

    @Test void providerRefundForComplaintUsesEscrowAndRecordsComplaintResolutionAtomically() {
        Scenario s = create(OrderStatus.PROCESSING, true);
        Complaint complaint = complaints.saveAndFlush(Complaint.builder()
                .customer(s.customer()).reporter(s.customer()).reportedUser(s.provider())
                .order(s.order()).subject("Yêu cầu hoàn tiền")
                .description("Provider đồng ý hoàn toàn bộ ký quỹ.")
                .status(ComplaintStatus.PROCESSING).createdAt(LocalDateTime.now()).build());

        escrow.refundComplaintByProvider(complaint.getId(), s.provider());

        assertFullRefund(s, true);
        Complaint resolved = complaints.findById(complaint.getId()).orElseThrow();
        assertEquals(ComplaintStatus.RESOLVED, resolved.getStatus());
        assertNotNull(resolved.getResolvedAt());
        assertTrue(resolved.getResolutionNotes().contains("hoàn 100% ký quỹ"));
        assertNull(resolved.getHandledBy(), "Provider action must not be recorded as an Admin.");
        assertThrows(IllegalStateException.class,
                () -> escrow.refundComplaintByProvider(complaint.getId(), s.provider()));
        assertEquals(1, transactions.findByOrderIdOrderByIdAsc(s.order().getId()).stream()
                .filter(row -> "UNHOLD_REFUND".equals(row.getType())).count());
    }

    @Test void providerCancelsAfterBothStartedAndClosesThird() {
        Scenario s = create(OrderStatus.PROCESSING, true);
        escrow.cancelByProvider(s.order().getId(), s.provider(), "Không thể tiếp tục");
        assertFullRefund(s, true);
        RentalOrder o = orders.findById(s.order().getId()).orElseThrow();
        assertEquals(new BigDecimal("300000.00"), o.getStartDepositAmount());
        assertNotNull(o.getStartDepositClosedAt());
        assertEquals(BookingStatus.CANCELLED,
                bookings.findById(s.order().getBooking().getId()).orElseThrow().getStatus());
    }

    @Test void providerCancelsAfterDeliveryBeforeCustomerAccepts() {
        Scenario s = create(OrderStatus.PENDING_CUSTOMER_ACCEPTANCE, false);
        escrow.cancelByProvider(s.order().getId(), s.provider(), "Đã bàn giao nhưng không nhận tiếp");
        assertFullRefund(s, true);
    }

    @Test void providerCancelsWhileCustomerWaitsForOtpClearsOtp() {
        Scenario s = create(OrderStatus.PENDING_PAYMENT_OTP, false);
        escrow.cancelByProvider(s.order().getId(), s.provider(), "Yêu cầu dừng trước OTP");
        assertFullRefund(s, true);
        RentalOrder o = orders.findById(s.order().getId()).orElseThrow();
        assertNull(o.getCompletionOtpHash());
        assertNull(o.getCompletionOtpExpiresAt());
        assertEquals(0, o.getCompletionOtpAttempts());
        assertThrows(IllegalStateException.class,
                () -> escrow.verifyOtpAndSettle(o.getId(), s.customer(), "123456"));
    }

    @Test void providerCancelsCustomerCancellationRequestAndRefundsFull() {
        Scenario s = create(OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER, false);
        escrow.cancelByProvider(s.order().getId(), s.provider(), "Chủ động hoàn toàn bộ");
        assertFullRefund(s, true);
        assertEquals("Customer đã yêu cầu dừng",
                orders.findById(s.order().getId()).orElseThrow().getCancellationCustomerReason());
    }

    @Test void providerCancelsWhileCustomerReviewsCompensation() {
        Scenario s = create(OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER, false);
        escrow.cancelByProvider(s.order().getId(), s.provider(), "Không yêu cầu bồi hoàn nữa");
        assertFullRefund(s, true);
    }

    @Test void providerCancelsDisputeAndResolvesItInCustomersFavor() {
        Scenario s = create(OrderStatus.CANCELLATION_DISPUTED, false);
        escrow.cancelByProvider(s.order().getId(), s.provider(), "Chấp nhận hoàn 100%");
        assertFullRefund(s, true);
        assertEquals("Không đồng ý chi phí",
                orders.findById(s.order().getId()).orElseThrow().getCancellationDisputeReason());
    }

    @Test void alreadySettledOrderCannotRefundAgainEvenWithLockedAmount() {
        Scenario s = create(OrderStatus.PROCESSING, false);
        RentalOrder order = s.order();
        order.setSettledAt(LocalDateTime.now());
        orders.saveAndFlush(order);
        assertThrows(IllegalStateException.class,
                () -> escrow.cancelByProvider(order.getId(), s.provider(), "Đã quyết toán"));
        assertEquals(PRICE, wallets.findByUser(s.customer()).orElseThrow().getLockedAmount());
        assertTrue(settlements.findByOrderId(order.getId()).isEmpty());
    }

    @Test void missingRefundTransactionIsFlaggedInAdminReconciliation() {
        Scenario s = create(OrderStatus.PROCESSING, false);
        escrow.cancelByProvider(s.order().getId(), s.provider(), "Hoàn đủ tiền");
        EscrowSettlement audit = settlements.findByOrderId(s.order().getId()).orElseThrow();
        assertTrue(reconciliation.reconcile(audit));
        WalletTransaction refund = transactions.findByOrderIdOrderByIdAsc(s.order().getId())
                .stream().filter(t -> "UNHOLD_REFUND".equals(t.getType())).findFirst().orElseThrow();
        transactions.deleteById(refund.getId());
        assertFalse(reconciliation.reconcile(audit));
    }

    @Test void unrelatedProviderCannotCancelOrder() {
        Scenario s = create(OrderStatus.PROCESSING, false);
        User unrelated = user(Role.PROVIDER);
        assertThrows(IllegalArgumentException.class,
                () -> escrow.cancelByProvider(s.order().getId(), unrelated, "Không có quyền"));
        assertEquals(PRICE, wallets.findByUser(s.customer()).orElseThrow().getLockedAmount());
        assertTrue(settlements.findByOrderId(s.order().getId()).isEmpty());
    }

    @Test void corruptedEscrowMustNotRefundOrCreateSnapshot() {
        Scenario s = create(OrderStatus.PROCESSING, false);
        UserWallet w = wallets.findByUser(s.customer()).orElseThrow();
        w.setLockedAmount(new BigDecimal("200000.00"));
        wallets.saveAndFlush(w);
        assertThrows(IllegalStateException.class,
                () -> escrow.cancelByProvider(s.order().getId(), s.provider(), "Sai ví"));
        assertEquals(PRICE, orders.findById(s.order().getId()).orElseThrow().getLockedAmount());
        assertTrue(settlements.findByOrderId(s.order().getId()).isEmpty());
        assertEquals(CUSTOMER_BEFORE, wallets.findByUser(s.customer()).orElseThrow().getBalance());
    }

    @Test void concurrentCancellationRequestsDoNotRefundTwice() throws Exception {
        Scenario s = create(OrderStatus.PROCESSING, false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> request = () -> {
                start.await();
                try { escrow.cancelByProvider(s.order().getId(), s.provider(), "Hai yêu cầu đồng thời"); return true; }
                catch (IllegalStateException | org.springframework.dao.DataAccessException e) { return false; }
            };
            Future<Boolean> f1 = pool.submit(request), f2 = pool.submit(request);
            start.countDown();
            assertNotEquals(f1.get(20, TimeUnit.SECONDS), f2.get(20, TimeUnit.SECONDS),
                    "Chỉ một yêu cầu được phép kết thúc đơn.");
        } finally { pool.shutdownNow(); }
        assertFullRefund(s, true);
        assertEquals(1, settlements.findAll().stream().filter(e ->
                e.getOrder().getId().equals(s.order().getId())).count());
    }
}
