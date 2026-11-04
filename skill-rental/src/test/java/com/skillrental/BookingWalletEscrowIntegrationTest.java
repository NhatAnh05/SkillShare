package com.skillrental;

import com.skillrental.dto.BookingForm;
import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.service.BookingService;
import com.skillrental.service.EscrowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BookingWalletEscrowIntegrationTest {
    private static final BigDecimal COST = new BigDecimal("350000.00");

    @Autowired private UserRepository users;
    @Autowired private ProviderProfileRepository providers;
    @Autowired private OfferedServiceRepository services;
    @Autowired private UserWalletRepository wallets;
    @Autowired private WalletTransactionRepository walletTransactions;
    @Autowired private BookingRepository bookings;
    @Autowired private RentalOrderRepository orders;
    @Autowired private BookingService bookingService;
    @Autowired private EscrowService escrow;
    @Autowired private MockMvc mvc;

    private User customer;
    private User provider;
    private ProviderProfile profile;
    private ServiceCatalog service;

    @BeforeEach
    void prepare() {
        customer = users.saveAndFlush(User.builder()
                .fullName("Customer kiểm thử ký quỹ")
                .email(UUID.randomUUID() + "@escrow.test")
                .password("test-password-not-used")
                .role(Role.CUSTOMER)
                .emailVerified(true)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        provider = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        profile = providers.findByUser(provider).orElseThrow();
        service = services.saveAndFlush(ServiceCatalog.builder()
                .provider(provider).name("Kiểm thử số dư & khóa tiền")
                .description("Gói công việc dùng kiểm tra khả năng giữ tiền trong ví.")
                .pricingType(ServicePricingType.FIXED).price(COST)
                .status(ServiceStatus.PUBLISHED).build());
    }

    private void deposit(BigDecimal amount) {
        wallets.saveAndFlush(UserWallet.builder()
                .user(customer).balance(amount).lockedAmount(BigDecimal.ZERO).build());
    }

    private BookingForm form() {
        BookingForm f = new BookingForm();
        f.setIdempotencyKey(UUID.randomUUID().toString());
        f.setServiceId(service.getId());
        f.setScheduleMode(ScheduleMode.APPOINTMENT);
        f.setBookingDate(LocalDate.now().plusDays(5));
        f.setTimeSlot(LocalTime.of(10, 0));
        f.setQuantity(1);
        f.setDurationHours(2);
        f.setAcceptedPolicy(true);
        return f;
    }

    @Test
    void walletMissingIsZeroAndNoBookingCreated() {
        long beforeBookings = bookings.count();
        long beforeOrders = orders.count();
        assertEquals(0, BigDecimal.ZERO.compareTo(escrow.availableBalance(customer)));
        EscrowService.InsufficientWalletBalanceException ex = assertThrows(
                EscrowService.InsufficientWalletBalanceException.class,
                () -> bookingService.create(customer, profile.getId(), form()));
        assertEquals(0, COST.compareTo(ex.getShortfall()));
        assertEquals(beforeBookings, bookings.count());
        assertEquals(beforeOrders, orders.count());
        assertTrue(wallets.findByUser(customer).isEmpty());
    }

    @Test
    void insufficientBalanceDoesNotCreateBookingOrderOrHold() {
        deposit(new BigDecimal("100000.00"));
        long beforeBookings = bookings.count();
        long beforeOrders = orders.count();
        EscrowService.InsufficientWalletBalanceException ex = assertThrows(
                EscrowService.InsufficientWalletBalanceException.class,
                () -> bookingService.create(customer, profile.getId(), form()));
        assertEquals(0, new BigDecimal("250000.00").compareTo(ex.getShortfall()));
        assertEquals(beforeBookings, bookings.count());
        assertEquals(beforeOrders, orders.count());
        var wallet = wallets.findByUser(customer).orElseThrow();
        assertEquals(0, new BigDecimal("100000.00").compareTo(wallet.getBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getLockedAmount()));
        assertTrue(walletTransactions.findByWalletOrderByCreatedAtDesc(wallet).isEmpty());
    }

    @Test
    void exactBalanceHoldsFullTotalOnceAndDoesNotPayProvider() {
        deposit(COST);
        var providerWalletBefore = wallets.findByUser(provider).orElseThrow().getBalance();
        BookingForm request = form();
        RentalOrder booked = bookingService.create(customer, profile.getId(), request);
        assertEquals(OrderStatus.PENDING_PROVIDER_ACCEPTANCE, booked.getStatus());
        assertEquals(0, COST.compareTo(booked.getLockedAmount()));
        var wallet = wallets.findByUser(customer).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getBalance()));
        assertEquals(0, COST.compareTo(wallet.getLockedAmount()));
        assertEquals(0, providerWalletBefore.compareTo(wallets.findByUser(provider).orElseThrow().getBalance()));
        assertEquals(1, walletTransactions.findByWalletOrderByCreatedAtDesc(wallet).stream()
                .filter(t -> "HOLD".equals(t.getType()) && t.getOrder().getId().equals(booked.getId()))
                .count());
        assertEquals(booked.getId(), bookingService.create(customer, profile.getId(), request).getId());
        assertEquals(1, walletTransactions.findByWalletOrderByCreatedAtDesc(wallet).stream()
                .filter(t -> "HOLD".equals(t.getType()) && t.getOrder().getId().equals(booked.getId()))
                .count());
        assertThrows(IllegalStateException.class, () -> escrow.reserveForBooking(booked));
    }

    @Test
    void separateKeysCannotSpendSameFundsTwice() {
        deposit(COST);
        bookingService.create(customer, profile.getId(), form());
        assertThrows(EscrowService.InsufficientWalletBalanceException.class,
                () -> bookingService.create(customer, profile.getId(), form()));
        var wallet = wallets.findByUser(customer).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getBalance()));
        assertEquals(0, COST.compareTo(wallet.getLockedAmount()));
        assertEquals(1, walletTransactions.findByWalletOrderByCreatedAtDesc(wallet).stream()
                .filter(t -> "HOLD".equals(t.getType())).count());
    }

    @Test
    void concurrentBookingsNeverLockMoreThanAvailable() throws Exception {
        deposit(COST);
        var first = form();
        var second = form();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> firstCall = () -> {
                start.await();
                try {
                    bookingService.create(customer, profile.getId(), first);
                    return true;
                } catch (RuntimeException rejected) {
                    return false;
                }
            };
            Callable<Boolean> secondCall = () -> {
                start.await();
                try {
                    bookingService.create(customer, profile.getId(), second);
                    return true;
                } catch (RuntimeException rejected) {
                    return false;
                }
            };
            Future<Boolean> a = pool.submit(firstCall);
            Future<Boolean> b = pool.submit(secondCall);
            start.countDown();
            int success = (a.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (b.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, success, "Chỉ một trong hai booking được giữ khoản tiền duy nhất.");
        } finally {
            pool.shutdownNow();
        }
        var wallet = wallets.findByUser(customer).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getBalance()));
        assertEquals(0, COST.compareTo(wallet.getLockedAmount()));
        assertEquals(1, walletTransactions.findByWalletOrderByCreatedAtDesc(wallet).stream()
                .filter(t -> "HOLD".equals(t.getType())).count());
    }

    @Test
    void bookingPageDisplaysBalanceAndInsufficientPostOffersTopUp() throws Exception {
        deposit(new BigDecimal("10000.00"));
        mvc.perform(get("/booking/provider/{id}", profile.getId())
                        .with(user(customer.getEmail()).roles("CUSTOMER"))
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("walletAvailable", new BigDecimal("10000.00")))
                .andExpect(view().name("booking/booking"));
        long beforeOrders = orders.count();
        mvc.perform(post("/booking/provider/{id}", profile.getId())
                        .with(user(customer.getEmail()).roles("CUSTOMER")).with(csrf())
                        .param("serviceId", service.getId().toString())
                        .param("idempotencyKey", UUID.randomUUID().toString())
                        .param("bookingDate", LocalDate.now().plusDays(5).toString())
                        .param("timeSlot", "10:00").param("scheduleMode", "APPOINTMENT")
                        .param("durationHours", "2").param("quantity", "1")
                        .param("acceptedPolicy", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/booking"))
                .andExpect(model().attributeExists("walletShortfall", "bookingError"));
        assertEquals(beforeOrders, orders.count());
    }
}
