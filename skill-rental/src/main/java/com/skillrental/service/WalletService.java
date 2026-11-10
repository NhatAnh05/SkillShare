package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service @RequiredArgsConstructor
public class WalletService {
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;

    @Transactional
    public Wallet getOrCreate(User user) {
        return wallets.findByUser(user).orElseGet(() -> wallets.save(Wallet.builder()
                .user(user)
                .availableBalance(BigDecimal.ZERO)
                .heldBalance(BigDecimal.ZERO)
                .totalDeposited(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .build()));
    }

    @Transactional(readOnly = true)
    public BigDecimal available(User user) {
        return nz(wallets.findByUser(user).map(Wallet::getAvailableBalance).orElse(BigDecimal.ZERO));
    }

    @Transactional(readOnly = true)
    public BigDecimal held(User user) {
        return nz(wallets.findByUser(user).map(Wallet::getHeldBalance).orElse(BigDecimal.ZERO));
    }

    /** Total held balance across every wallet, used by admin escrow reconciliation. */
    @Transactional(readOnly = true)
    public BigDecimal totalHeldAcrossWallets() {
        return wallets.findAll().stream()
                .map(Wallet::getHeldBalance)
                .map(WalletService::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<WalletTransaction> history(User user) {
        return wallets.findByUser(user)
                .map(w -> transactions.findByWalletOrderByIdDesc(w))
                .orElseGet(List::of);
    }

    @Transactional
    public Wallet deposit(User user, BigDecimal amount, String method) {
        BigDecimal value = positive(amount, "Số tiền nạp");
        Wallet wallet = getOrCreate(user);
        wallet.setAvailableBalance(nz(wallet.getAvailableBalance()).add(value));
        wallet.setTotalDeposited(nz(wallet.getTotalDeposited()).add(value));
        wallets.save(wallet);
        record(wallet, WalletTransactionType.DEPOSIT, value, "Nạp tiền vào ví qua " + safe(method));
        return wallet;
    }

    @Transactional
    public Wallet withdraw(User user, BigDecimal amount) {
        BigDecimal value = positive(amount, "Số tiền rút");
        Wallet wallet = getOrCreate(user);
        BigDecimal available = nz(wallet.getAvailableBalance());
        if (available.compareTo(value) < 0) {
            throw new InsufficientFundsException(value, available);
        }
        wallet.setAvailableBalance(available.subtract(value));
        wallet.setTotalWithdrawn(nz(wallet.getTotalWithdrawn()).add(value));
        wallets.save(wallet);
        record(wallet, WalletTransactionType.WITHDRAWAL, value, "Rút tiền về tài khoản ngân hàng");
        return wallet;
    }

    @Transactional
    public void requireAvailable(User user, BigDecimal amount) {
        BigDecimal available = available(user);
        BigDecimal value = positive(amount, "Số tiền cần khóa");
        if (available.compareTo(value) < 0) {
            throw new InsufficientFundsException(value, available);
        }
    }

    @Transactional
    public void hold(User user, BigDecimal amount, Long orderId, String description) {
        BigDecimal value = positive(amount, "Số tiền khóa");
        Wallet wallet = getOrCreate(user);
        BigDecimal available = nz(wallet.getAvailableBalance());
        if (available.compareTo(value) < 0) {
            throw new InsufficientFundsException(value, available);
        }
        wallet.setAvailableBalance(available.subtract(value));
        wallet.setHeldBalance(nz(wallet.getHeldBalance()).add(value));
        wallets.save(wallet);
        record(wallet, WalletTransactionType.HOLD, value, description, orderId);
    }

    @Transactional
    public void releaseHold(User user, BigDecimal amount, Long orderId, String description) {
        BigDecimal value = positive(amount, "Số tiền hoàn khóa");
        Wallet wallet = getOrCreate(user);
        BigDecimal held = nz(wallet.getHeldBalance());
        BigDecimal release = value.min(held);
        wallet.setHeldBalance(held.subtract(release));
        wallet.setAvailableBalance(nz(wallet.getAvailableBalance()).add(release));
        wallets.save(wallet);
        record(wallet, WalletTransactionType.RELEASE, release, description, orderId);
    }

    @Transactional
    public void payHeldToProvider(User customer, User provider, BigDecimal amount, Long orderId,
                                  WalletTransactionType providerType, String description) {
        BigDecimal value = positive(amount, "Số tiền thanh toán");
        Wallet customerWallet = getOrCreate(customer);
        BigDecimal held = nz(customerWallet.getHeldBalance());
        BigDecimal pay = value.min(held);
        customerWallet.setHeldBalance(held.subtract(pay));
        wallets.save(customerWallet);
        record(customerWallet, WalletTransactionType.RELEASE, pay,
                "Giải ngân escrow cho Provider: " + safe(description), orderId);

        Wallet providerWallet = getOrCreate(provider);
        providerWallet.setAvailableBalance(nz(providerWallet.getAvailableBalance()).add(pay));
        wallets.save(providerWallet);
        record(providerWallet, providerType, pay, safe(description), orderId);
    }

    @Transactional
    public void creditProvider(User provider, BigDecimal amount, Long orderId,
                               WalletTransactionType type, String description) {
        BigDecimal value = positive(amount, "Số tiền ghi có");
        Wallet wallet = getOrCreate(provider);
        wallet.setAvailableBalance(nz(wallet.getAvailableBalance()).add(value));
        wallets.save(wallet);
        record(wallet, type, value, safe(description), orderId);
    }

    @Transactional
    public void clawbackProvider(User provider, BigDecimal amount, Long orderId, String description) {
        BigDecimal value = positive(amount, "Số tiền thu hồi");
        Wallet wallet = getOrCreate(provider);
        wallet.setAvailableBalance(nz(wallet.getAvailableBalance()).subtract(value));
        wallets.save(wallet);
        record(wallet, WalletTransactionType.CLAWBACK, value, safe(description), orderId);
    }

    @Transactional
    public void refundCustomer(User customer, BigDecimal amount, Long orderId, String description) {
        if (amount == null || amount.signum() <= 0) return;
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        Wallet wallet = getOrCreate(customer);
        wallet.setAvailableBalance(nz(wallet.getAvailableBalance()).add(value));
        wallets.save(wallet);
        record(wallet, WalletTransactionType.REFUND, value, safe(description), orderId);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalEarnings(User provider) {
        Wallet wallet = wallets.findByUser(provider).orElse(null);
        if (wallet == null) return BigDecimal.ZERO;
        return transactions.findByWalletOrderByIdDesc(wallet).stream()
                .filter(t -> t.getType() == WalletTransactionType.PROVIDER_EARNING
                        || t.getType() == WalletTransactionType.ADVANCE_PAYMENT
                        || t.getType() == WalletTransactionType.FINAL_PAYMENT
                        || t.getType() == WalletTransactionType.CANCEL_COMPENSATION)
                .map(WalletTransaction::getAmount)
                .map(WalletService::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void record(Wallet wallet, WalletTransactionType type, BigDecimal amount, String description) {
        record(wallet, type, amount, description, null);
    }

    private void record(Wallet wallet, WalletTransactionType type, BigDecimal amount, String description,
                        Long orderId) {
        transactions.save(WalletTransaction.builder()
                .wallet(wallet)
                .type(type)
                .amount(nz(amount).setScale(2, RoundingMode.HALF_UP))
                .balanceAfter(nz(wallet.getAvailableBalance()))
                .heldAfter(nz(wallet.getHeldBalance()))
                .referenceType(orderId == null ? null : "ORDER")
                .referenceId(orderId)
                .description(description)
                .build());
    }

    private static BigDecimal positive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + " phải lớn hơn 0.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
