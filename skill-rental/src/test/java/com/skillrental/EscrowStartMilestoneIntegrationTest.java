package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.service.EscrowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 4 regression: independent acknowledgements and one non-paying 1/3 escrow earmark. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EscrowStartMilestoneIntegrationTest {
    @Autowired private EscrowService escrow;
    @Autowired private RentalOrderRepository orders;
    @Autowired private RentalOrderStatusHistoryRepository history;
    @Autowired private UserRepository users;
    @Autowired private UserWalletRepository wallets;
    @Autowired private WalletTransactionRepository transactions;

    private User customer() { return users.findByEmailIgnoreCase("customer@skillrental.vn").orElseThrow(); }
    private User provider() { return users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow(); }

    private RentalOrder prepare(String total) {
        User customer = customer();
        BigDecimal amount = new BigDecimal(total);
        UserWallet wallet = wallets.findByUser(customer).orElseThrow();
        wallet.setBalance(wallet.getBalance().add(amount));
        wallets.saveAndFlush(wallet);
        RentalOrder order = orders.saveAndFlush(RentalOrder.builder()
                .customer(customer).provider(provider()).subtotal(amount).discount(BigDecimal.ZERO)
                .total(amount).createdAt(LocalDateTime.now())
                .status(OrderStatus.PENDING_PROVIDER_ACCEPTANCE).build());
        escrow.reserveForBooking(order);
        escrow.providerAccept(order.getId(), provider());
        return orders.findById(order.getId()).orElseThrow();
    }

    @Test
    void eachPartyAcknowledgesAndOnlySecondConfirmationEarmarksOneThird() {
        RentalOrder created = prepare("100000.00");
        Long id = created.getId();
        UserWallet beforeCustomer = wallets.findByUser(customer()).orElseThrow();
        BigDecimal availableBefore = beforeCustomer.getBalance();
        BigDecimal heldBefore = beforeCustomer.getLockedAmount();
        BigDecimal providerBefore = wallets.findByUser(provider()).orElseThrow().getBalance();
        long customerTxCount = transactions.findByWalletOrderByCreatedAtDesc(beforeCustomer).size();
        escrow.confirmStart(id, customer(), Role.CUSTOMER);
        RentalOrder one = orders.findById(id).orElseThrow();
        assertEquals(OrderStatus.PENDING_START_CONFIRMATION, one.getStatus());
        assertTrue(one.getCustomerStarted());
        assertFalse(one.getProviderStarted());
        assertNotNull(one.getCustomerStartConfirmedAt());
        assertNull(one.getProviderStartConfirmedAt());
        assertNull(one.getStartDepositAmount());
        assertNull(one.getStartDepositState());
        assertNull(one.getWorkStartedAt());
        escrow.confirmStart(id, provider(), Role.PROVIDER);
        RentalOrder both = orders.findById(id).orElseThrow();
        assertEquals(OrderStatus.PROCESSING, both.getStatus());
        assertTrue(both.getCustomerStarted());
        assertTrue(both.getProviderStarted());
        assertNotNull(both.getProviderStartConfirmedAt());
        assertNotNull(both.getWorkStartedAt());
        assertEquals(new BigDecimal("33333.33"), both.getStartDepositAmount());
        assertEquals(StartDepositState.RESERVED, both.getStartDepositState());
        assertNotNull(both.getStartDepositReservedAt());
        assertEquals(new BigDecimal("100000.00"), both.getLockedAmount());
        UserWallet after = wallets.findByUser(customer()).orElseThrow();
        assertEquals(availableBefore, after.getBalance());
        assertEquals(heldBefore, after.getLockedAmount());
        assertEquals(providerBefore, wallets.findByUser(provider()).orElseThrow().getBalance());
        assertEquals(customerTxCount, transactions.findByWalletOrderByCreatedAtDesc(after).size(),
                "Only acknowledging the start must not create a money movement.");
        assertTrue(history.findByOrderIdOrderByCreatedAtAsc(id).stream()
                .anyMatch(row -> row.getNote() != null && row.getNote().contains("33333.33")));
    }

    @Test
    void providerFirstAlsoWorksAndDuplicateAcknowledgementsCannotEarmarkAgain() {
        Long id = prepare("100000.01").getId();
        escrow.confirmStart(id, provider(), Role.PROVIDER);
        assertEquals(OrderStatus.PENDING_START_CONFIRMATION, orders.findById(id).orElseThrow().getStatus());
        assertThrows(IllegalStateException.class, () -> escrow.confirmStart(id, provider(), Role.PROVIDER));
        escrow.confirmStart(id, customer(), Role.CUSTOMER);
        RentalOrder started = orders.findById(id).orElseThrow();
        assertEquals(new BigDecimal("33333.34"), started.getStartDepositAmount());
        assertThrows(IllegalStateException.class, () -> escrow.confirmStart(id, customer(), Role.CUSTOMER));
        assertEquals(new BigDecimal("33333.34"), orders.findById(id).orElseThrow().getStartDepositAmount());
    }

    @Test
    void otherRolesAndWrongActorCannotAcknowledgeStart() {
        Long id = prepare("900000.00").getId();
        assertThrows(IllegalArgumentException.class, () -> escrow.confirmStart(id, customer(), Role.PROVIDER));
        assertThrows(IllegalArgumentException.class, () -> escrow.confirmStart(id, customer(), Role.ADMIN));
        User otherCustomer = users.saveAndFlush(User.builder()
                .fullName("Customer không thuộc đơn")
                .email("other-start-test@skillshare.test")
                .password("unused-password-hash-test-only")
                .role(Role.CUSTOMER).emailVerified(true).accountStatus(AccountStatus.ACTIVE)
                .build());
        assertThrows(IllegalArgumentException.class, () -> escrow.confirmStart(id, otherCustomer, Role.CUSTOMER));
        assertNull(orders.findById(id).orElseThrow().getStartDepositAmount());
    }

    @Test
    void cancellationBeforeBothConfirmDoesNotCreateMilestone() {
        Long id = prepare("900000.00").getId();
        UserWallet before = wallets.findByUser(customer()).orElseThrow();
        BigDecimal sum = before.getBalance().add(before.getLockedAmount());
        escrow.confirmStart(id, customer(), Role.CUSTOMER);
        escrow.cancelByProvider(id, provider(), "Không thể nhận việc");
        RentalOrder cancelled = orders.findById(id).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertNull(cancelled.getStartDepositState());
        assertNull(cancelled.getStartDepositAmount());
        assertThrows(IllegalStateException.class, () -> escrow.confirmStart(id, provider(), Role.PROVIDER));
        UserWallet after = wallets.findByUser(customer()).orElseThrow();
        assertEquals(sum, after.getBalance().add(after.getLockedAmount()));
    }

    @Test
    void providerCancellationAfterStartClosesMilestoneAndRefundsWholeHold() {
        Long id = prepare("900000.00").getId();
        UserWallet before = wallets.findByUser(customer()).orElseThrow();
        BigDecimal sumBefore = before.getBalance().add(before.getLockedAmount());
        BigDecimal providerBefore = wallets.findByUser(provider()).orElseThrow().getBalance();
        escrow.confirmStart(id, provider(), Role.PROVIDER);
        escrow.confirmStart(id, customer(), Role.CUSTOMER);
        assertEquals(StartDepositState.RESERVED, orders.findById(id).orElseThrow().getStartDepositState());
        escrow.cancelByProvider(id, provider(), "Tạm ngừng");
        RentalOrder order = orders.findById(id).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(StartDepositState.CLOSED, order.getStartDepositState());
        assertNotNull(order.getStartDepositClosedAt());
        assertEquals(new BigDecimal("300000.00"), order.getStartDepositAmount());
        assertEquals(0, order.getLockedAmount().signum());
        UserWallet after = wallets.findByUser(customer()).orElseThrow();
        assertEquals(sumBefore, after.getBalance().add(after.getLockedAmount()));
        assertEquals(providerBefore, wallets.findByUser(provider()).orElseThrow().getBalance());
        assertThrows(IllegalStateException.class, () -> escrow.cancelByProvider(id, provider(), "Tạm ngừng"));
    }

    @Test
    void twoConcurrentConfirmationsCreateExactlyOneMilestone() throws Exception {
        Long id = prepare("900000.00").getId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            Future<?> customerTask = pool.submit(() -> {
                try { gate.await(); escrow.confirmStart(id, customer(), Role.CUSTOMER); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            });
            Future<?> providerTask = pool.submit(() -> {
                try { gate.await(); escrow.confirmStart(id, provider(), Role.PROVIDER); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            });
            gate.countDown();
            customerTask.get(20, TimeUnit.SECONDS);
            providerTask.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        RentalOrder finalOrder = orders.findById(id).orElseThrow();
        assertEquals(OrderStatus.PROCESSING, finalOrder.getStatus());
        assertEquals(StartDepositState.RESERVED, finalOrder.getStartDepositState());
        assertEquals(new BigDecimal("300000.00"), finalOrder.getStartDepositAmount());
        List<RentalOrderStatusHistory> historyRows = history.findByOrderIdOrderByCreatedAtAsc(id);
        assertEquals(1, historyRows.stream().filter(row -> row.getNote() != null
                && row.getNote().contains("Ghi nhận mốc bảo đảm 1/3")).count());
    }
}
