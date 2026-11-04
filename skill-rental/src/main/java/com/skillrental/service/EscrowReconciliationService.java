package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.repository.EscrowSettlementRepository;
import com.skillrental.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Cross-checks the settlement snapshot against actual wallet transaction rows. */
@Service
@RequiredArgsConstructor
public class EscrowReconciliationService {
    private final EscrowSettlementRepository settlements;
    private final com.skillrental.repository.EscrowDisputeDecisionRepository disputeDecisions;
    private final WalletTransactionRepository transactions;

    public record AuditRow(EscrowSettlement settlement, boolean reconciled) {}

    @Transactional(readOnly = true)
    public List<AuditRow> latest() {
        return settlements.findTop20ByOrderBySettledAtDescIdDesc().stream()
                .map(e -> new AuditRow(e, reconcile(e))).toList();
    }

    @Transactional(readOnly = true)
    public boolean reconcile(EscrowSettlement entry) {
        if (entry == null || !entry.isBalanced() || entry.getOrder() == null) return false;
        RentalOrder order = entry.getOrder();
        if (order.getCustomer() == null || order.getProvider() == null
                || order.getCustomer().getId() == null || order.getProvider().getId() == null
                || entry.getSettledAt() == null || order.getSettledAt() == null
                || order.getLockedAmount() == null || order.getLockedAmount().signum() != 0
                || !sameAmount(order.getPlatformFeeAmount(), entry.getPlatformFeeAmount())
                || !sameAmount(order.getProviderNetAmount(), entry.getProviderNetAmount())) {
            return false;
        }
        boolean completion = "COMPLETION".equals(entry.getKind());
        boolean customerCancellation = "CUSTOMER_CANCELLATION".equals(entry.getKind());
        boolean providerCancellation = "PROVIDER_CANCELLATION".equals(entry.getKind());
        boolean customerEarlyCancel = "CUSTOMER_CANCEL_EARLY".equals(entry.getKind());
        boolean adminDispute = "ADMIN_DISPUTE".equals(entry.getKind());
        if (!completion && !customerCancellation && !providerCancellation && !customerEarlyCancel && !adminDispute) return false;
        if (completion && order.getStatus() != OrderStatus.COMPLETED) return false;
        if (!completion && order.getStatus() != OrderStatus.CANCELLED) return false;

        List<WalletTransaction> rows = transactions.findByOrderIdOrderByIdAsc(order.getId());
        Long customerId = order.getCustomer().getId(), providerId = order.getProvider().getId();
        if (providerCancellation || customerEarlyCancel) {
            // Early Customer or Provider cancellation: REFUND ONLY, without fees or Provider credit.
            Long expectedActorId = providerCancellation ? providerId : customerId;
            return order.getCancelledBy() != null
                    && Objects.equals(order.getCancelledBy().getId(), expectedActorId)
                    && entry.getSettledBy() != null
                    && Objects.equals(entry.getSettledBy().getId(), expectedActorId)
                    && (!customerEarlyCancel || order.getWorkStartedAt() == null)
                    && entry.getGrossAmount().signum() == 0
                    && entry.getPlatformFeeAmount().signum() == 0
                    && entry.getProviderNetAmount().signum() == 0
                    && entry.getCustomerRefundAmount().compareTo(entry.getHeldBefore()) == 0
                    && expectedCount(rows, "UNHOLD_REFUND", customerId, entry.getCustomerRefundAmount())
                    && expectedCount(rows, "SETTLEMENT_OUT", customerId, BigDecimal.ZERO)
                    && expectedCount(rows, "CANCELLATION_REFUND", customerId, BigDecimal.ZERO)
                    && expectedCount(rows, "ADMIN_DISPUTE_OUT", customerId, BigDecimal.ZERO)
                    && expectedCount(rows, "SETTLEMENT_IN", providerId, BigDecimal.ZERO);
        }
        if (adminDispute) {
            var decision = disputeDecisions.findByOrderId(order.getId());
            if (decision.isEmpty() || entry.getSettledBy() == null || entry.getSettledBy().getRole() != Role.ADMIN
                    || order.getCancelledBy() == null
                    || !Objects.equals(entry.getSettledBy().getId(), order.getCancelledBy().getId())
                    || !Objects.equals(decision.get().getAdmin().getId(), entry.getSettledBy().getId())
                    || !sameAmount(decision.get().getProviderAwarded(), entry.getGrossAmount())
                    || !sameAmount(decision.get().getCustomerRefunded(), entry.getCustomerRefundAmount())) return false;
        }
        if (adminDispute && (!expectedCount(rows, "SETTLEMENT_OUT", customerId, BigDecimal.ZERO)
                || !expectedCount(rows, "CANCELLATION_REFUND", customerId, BigDecimal.ZERO))) return false;
        String debitType = completion ? "SETTLEMENT_OUT" : adminDispute ? "ADMIN_DISPUTE_OUT" : "CANCELLATION_REFUND";
        BigDecimal grossDebit = entry.getGrossAmount().negate();
        return exactlyOne(rows, debitType, customerId, grossDebit)
                && expectedCount(rows, "UNHOLD_REFUND", customerId, entry.getCustomerRefundAmount())
                && expectedCount(rows, "SETTLEMENT_IN", providerId, entry.getProviderNetAmount());
    }

    private boolean exactlyOne(List<WalletTransaction> rows, String type, Long userId, BigDecimal amount) {
        List<WalletTransaction> matched = rows.stream().filter(t -> type.equals(t.getType())
                && t.getWallet() != null && t.getWallet().getUser() != null
                && Objects.equals(t.getWallet().getUser().getId(), userId)).toList();
        return matched.size() == 1 && sameAmount(matched.get(0).getAmount(), amount);
    }

    private boolean expectedCount(List<WalletTransaction> rows, String type, Long userId, BigDecimal amount) {
        if (amount == null) return false;
        List<WalletTransaction> matched = rows.stream().filter(t -> type.equals(t.getType())
                && t.getWallet() != null && t.getWallet().getUser() != null
                && Objects.equals(t.getWallet().getUser().getId(), userId)).toList();
        if (amount.signum() == 0) return matched.isEmpty();
        return matched.size() == 1 && sameAmount(matched.get(0).getAmount(), amount);
    }

    private boolean sameAmount(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) == 0; }
}
