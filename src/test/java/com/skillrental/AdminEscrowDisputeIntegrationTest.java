package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Phase 9: Admin arbitration of a disputed Customer cancellation, without creating money. */
@SpringBootTest
class AdminEscrowDisputeIntegrationTest {
    private static final BigDecimal TOTAL = new BigDecimal("900000.00");
    private static final BigDecimal AVAILABLE = new BigDecimal("100000.00");
    private static final BigDecimal PROVIDER_AVAILABLE = new BigDecimal("120000.00");
    private static final String DECISION = "Đã đối chiếu bản bàn giao và xác nhận phần việc Provider hoàn thành.";
    @Autowired EscrowService escrow;
    @Autowired RentalOrderRepository orders;
    @Autowired UserWalletRepository wallets;
    @Autowired UserRepository users;
    @Autowired EscrowSettlementRepository settlements;
    @Autowired EscrowDisputeDecisionRepository decisions;
    @Autowired WalletTransactionRepository transactions;
    @Autowired EscrowReconciliationService audit;
    @Autowired RentalOrderStatusHistoryRepository histories;

    record Scenario(User customer, User provider, User admin, RentalOrder order) {}

    User newUser(Role role) {
        return users.saveAndFlush(User.builder().fullName("Phase9 " + role)
                .email(UUID.randomUUID() + "@phase9.test").password("unused")
                .role(role).emailVerified(true).accountStatus(AccountStatus.ACTIVE).build());
    }
    Scenario scenario(OrderStatus status) {
        User customer = newUser(Role.CUSTOMER), provider = newUser(Role.PROVIDER), admin = newUser(Role.ADMIN);
        wallets.saveAndFlush(UserWallet.builder().user(customer).balance(AVAILABLE).lockedAmount(TOTAL).build());
        wallets.saveAndFlush(UserWallet.builder().user(provider).balance(PROVIDER_AVAILABLE).lockedAmount(BigDecimal.ZERO).build());
        RentalOrder o = orders.saveAndFlush(RentalOrder.builder().customer(customer).provider(provider)
                .subtotal(TOTAL).discount(BigDecimal.ZERO).total(TOTAL).lockedAmount(TOTAL)
                .status(status).providerAccepted(true).customerStarted(true).providerStarted(true)
                .workStartedAt(LocalDateTime.now().minusDays(1))
                .startDepositAmount(new BigDecimal("300000.00"))
                .startDepositState(StartDepositState.RESERVED)
                .cancellationCompensation(new BigDecimal("450000.00"))
                .cancellationCustomerReason("Customer yêu cầu hủy vì thay đổi phạm vi công việc")
                .cancellationWorkSummary("Provider đã hoàn thiện thiết kế và gửi bản nháp cho Customer")
                .cancellationEvidence("Bản thiết kế đã gửi qua hệ thống, kèm liên kết và lịch sử tin nhắn")
                .cancellationDisputeReason("Mức phí Provider đề xuất cao so với sản phẩm đã bàn giao")
                .cancellationDisputedAt(LocalDateTime.now().minusHours(2))
                .createdAt(LocalDateTime.now().minusDays(3)).build());
        return new Scenario(customer,provider,admin,o);
    }
    void unchanged(Scenario s) {
        assertEquals(TOTAL, wallets.findByUser(s.customer()).orElseThrow().getLockedAmount());
        assertEquals(AVAILABLE, wallets.findByUser(s.customer()).orElseThrow().getBalance());
        assertEquals(PROVIDER_AVAILABLE, wallets.findByUser(s.provider()).orElseThrow().getBalance());
        assertTrue(settlements.findByOrderId(s.order().getId()).isEmpty());
        assertTrue(decisions.findByOrderId(s.order().getId()).isEmpty());
    }
    @Test void adminPartialAwardBalancesWalletsAndPersistsEvidence() {
        Scenario s = scenario(OrderStatus.CANCELLATION_DISPUTED);
        Long id = s.order().getId();
        escrow.resolveDisputeByAdmin(id,s.admin(),new BigDecimal("270000.00"),DECISION);
        RentalOrder o = orders.findById(id).orElseThrow();
        assertEquals(OrderStatus.CANCELLED,o.getStatus());
        assertEquals(StartDepositState.CLOSED,o.getStartDepositState());
        assertEquals(0,o.getLockedAmount().signum());
        assertEquals(new BigDecimal("730000.00"),wallets.findByUser(s.customer()).orElseThrow().getBalance());
        assertEquals(0,wallets.findByUser(s.customer()).orElseThrow().getLockedAmount().signum());
        assertEquals(new BigDecimal("376500.00"),wallets.findByUser(s.provider()).orElseThrow().getBalance());
        assertEquals(new BigDecimal("13500.00"),o.getPlatformFeeAmount());
        assertEquals(new BigDecimal("256500.00"),o.getProviderNetAmount());
        var d = decisions.findByOrderId(id).orElseThrow();
        assertEquals(new BigDecimal("450000.00"),d.getProviderProposed());
        assertEquals(new BigDecimal("270000.00"),d.getProviderAwarded());
        assertEquals(new BigDecimal("630000.00"),d.getCustomerRefunded());
        assertEquals(DECISION,d.getDecisionReason());
        var entry = settlements.findByOrderId(id).orElseThrow();
        assertEquals("ADMIN_DISPUTE",entry.getKind());
        assertTrue(audit.reconcile(entry));
        assertTrue(histories.findByOrderIdOrderByCreatedAtAsc(id).stream()
                .anyMatch(h -> h.getChangedBy() != null && h.getChangedBy().getId().equals(s.admin().getId())));
        assertThrows(IllegalStateException.class,
                () -> escrow.resolveDisputeByAdmin(id,s.admin(),BigDecimal.ZERO,DECISION));
        assertEquals(1,decisions.findAll().stream().filter(x -> x.getOrder().getId().equals(id)).count());
    }
    @Test void zeroAwardRefundsFullHoldWithoutProviderCredit() {
        Scenario s = scenario(OrderStatus.CANCELLATION_DISPUTED);
        escrow.resolveDisputeByAdmin(s.order().getId(),s.admin(),BigDecimal.ZERO,DECISION);
        assertEquals(new BigDecimal("1000000.00"),wallets.findByUser(s.customer()).orElseThrow().getBalance());
        assertEquals(PROVIDER_AVAILABLE,wallets.findByUser(s.provider()).orElseThrow().getBalance());
        var e=settlements.findByOrderId(s.order().getId()).orElseThrow();
        assertEquals(0,e.getGrossAmount().signum());
        assertTrue(audit.reconcile(e));
        assertFalse(transactions.findByOrderIdOrderByIdAsc(s.order().getId()).stream()
                .anyMatch(tx -> "SETTLEMENT_IN".equals(tx.getType())));
    }
    @Test void fullAwardTransfersEntireHoldWithoutRefund() {
        Scenario s = scenario(OrderStatus.CANCELLATION_DISPUTED);
        escrow.resolveDisputeByAdmin(s.order().getId(),s.admin(),TOTAL,DECISION);
        assertEquals(AVAILABLE,wallets.findByUser(s.customer()).orElseThrow().getBalance());
        assertEquals(new BigDecimal("975000.00"),wallets.findByUser(s.provider()).orElseThrow().getBalance());
        assertTrue(audit.reconcile(settlements.findByOrderId(s.order().getId()).orElseThrow()));
    }
    @Test void refusesNonAdminWrongStatusAndInvalidAmountsWithoutMovingMoney() {
        Scenario s = scenario(OrderStatus.CANCELLATION_DISPUTED);
        Long id=s.order().getId();
        assertThrows(IllegalArgumentException.class,
                () -> escrow.resolveDisputeByAdmin(id,s.customer(),new BigDecimal("270000.00"),DECISION));
        assertThrows(IllegalArgumentException.class,
                () -> escrow.resolveDisputeByAdmin(id,s.admin(),new BigDecimal("900001"),DECISION));
        assertThrows(IllegalArgumentException.class,
                () -> escrow.resolveDisputeByAdmin(id,s.admin(),new BigDecimal("270000"),"ngắn"));
        unchanged(s);
        Scenario notDisputed = scenario(OrderStatus.PROCESSING);
        assertThrows(IllegalStateException.class,
                () -> escrow.resolveDisputeByAdmin(notDisputed.order().getId(),notDisputed.admin(),BigDecimal.ZERO,DECISION));
        unchanged(notDisputed);
    }
    @Test void insufficientWalletHoldRejectsDecisionWithNoPartialTransfer() {
        Scenario s = scenario(OrderStatus.CANCELLATION_DISPUTED);
        UserWallet w = wallets.findByUser(s.customer()).orElseThrow();
        w.setLockedAmount(new BigDecimal("1000.00")); wallets.saveAndFlush(w);
        assertThrows(IllegalStateException.class,
                () -> escrow.resolveDisputeByAdmin(s.order().getId(),s.admin(),new BigDecimal("1000.00"),DECISION));
        assertEquals(new BigDecimal("1000.00"),wallets.findByUser(s.customer()).orElseThrow().getLockedAmount());
        assertTrue(decisions.findByOrderId(s.order().getId()).isEmpty());
        assertTrue(settlements.findByOrderId(s.order().getId()).isEmpty());
    }
    @Test void reconciliationDetectsMissingProviderPosting() {
        Scenario s = scenario(OrderStatus.CANCELLATION_DISPUTED);
        escrow.resolveDisputeByAdmin(s.order().getId(),s.admin(),new BigDecimal("270000.00"),DECISION);
        var entry=settlements.findByOrderId(s.order().getId()).orElseThrow();
        assertTrue(audit.reconcile(entry));
        var providerTx = transactions.findByOrderIdOrderByIdAsc(s.order().getId()).stream()
                .filter(x -> "SETTLEMENT_IN".equals(x.getType())).findFirst().orElseThrow();
        transactions.deleteAndFlush(providerTx);
        assertFalse(audit.reconcile(entry));
    }
}
