package com.skillrental;

import com.skillrental.dto.BookingForm;
import com.skillrental.entity.*;
import com.skillrental.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CustomerProviderAdminTransactionFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ProviderProfileRepository providers;
    @Autowired private OfferedServiceRepository services;
    @Autowired private CartRepository carts;
    @Autowired private CartItemRepository cartItems;
    @Autowired private BookingRepository bookings;
    @Autowired private RentalOrderRepository orders;
    @Autowired private RentalOrderItemRepository orderItems;
    @Autowired private RentalOrderStatusHistoryRepository history;

    @Test
    @WithMockUser(username = "customer@skillrental.vn", roles = "CUSTOMER")
    void cartBookingOrderProviderCustomerAndAdminFlowPersistsAndIsIdempotent() throws Exception {
        User customer = users.findByEmailIgnoreCase("customer@skillrental.vn").orElseThrow();
        User provider = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        ProviderProfile providerProfile = providers.findByUser(provider).orElseThrow();

        mvc.perform(post("/provider/services/new").with(csrf())
                        .with(user(provider.getEmail()).roles("PROVIDER"))
                        .param("name", "Dịch vụ E2E ổn định")
                        .param("category", "Kiểm thử")
                        .param("description", "Dịch vụ dùng để kiểm chứng luồng giao dịch.")
                        .param("pricingType", "FIXED")
                        .param("price", "1250000.00")
                        .param("durationMinutes", "90")
                        .param("coverImageUrl", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/services"));
        ServiceCatalog service = services.findByProviderOrderByCreatedAtDesc(provider).stream()
                .filter(row -> row.getName().equals("Dịch vụ E2E ổn định"))
                .findFirst().orElseThrow();
        assertEquals(ServiceStatus.DRAFT, service.getStatus());
        mvc.perform(post("/admin/services/{id}/approve", service.getId()).with(csrf())
                        .with(user("admin@skillrental.vn").roles("ADMIN")))
                .andExpect(status().is3xxRedirection());
        service = services.findById(service.getId()).orElseThrow();
        assertEquals(ServiceStatus.PUBLISHED, service.getStatus());

        mvc.perform(get("/services"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("services",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.hasProperty("id",
                                org.hamcrest.Matchers.is(service.getId())))));
        mvc.perform(post("/cart/items").with(csrf()).param("serviceId", service.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        Cart cart = carts.findFirstByCustomerAndStatusOrderByUpdatedAtDesc(customer, CartStatus.ACTIVE).orElseThrow();
        CartItem cartItem = cartItems.findByCartAndServiceId(cart, service.getId()).orElseThrow();
        if (cartItem.getQuantity() != 1) {
            mvc.perform(post("/cart/items/{id}/remove", cartItem.getId()).with(csrf()))
                    .andExpect(status().is3xxRedirection());
            mvc.perform(post("/cart/items").with(csrf()).param("serviceId", service.getId().toString()))
                    .andExpect(status().is3xxRedirection());
            cartItem = cartItems.findByCartAndServiceId(cart, service.getId()).orElseThrow();
        }
        assertEquals(1, cartItem.getQuantity());

        mvc.perform(get("/cart/items/{id}/booking", cartItem.getId()))
                .andExpect(status().is3xxRedirection());

        MvcResult formResult = mvc.perform(get("/booking/provider/{id}", providerProfile.getId())
                        .param("serviceId", service.getId().toString())
                        .param("cartItemId", cartItem.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("form", "availableServices"))
                .andReturn();
        BookingForm form = (BookingForm) formResult.getModelAndView().getModel().get("form");
        String key = form.getIdempotencyKey();
        String day = LocalDate.now().plusDays(10).toString();
        long bookingCount = bookings.count();
        long orderCount = orders.count();

        mvc.perform(post("/booking/provider/{id}", providerProfile.getId()).with(csrf())
                        .param("serviceId", service.getId().toString())
                        .param("cartItemId", cartItem.getId().toString())
                        .param("idempotencyKey", key)
                        .param("bookingDate", day)
                        .param("timeSlot", "10:00")
                        .param("durationHours", "2")
                        .param("directBooking", "true")
                        .param("note", "Yêu cầu trong kiểm thử tích hợp"))
                .andExpect(status().is3xxRedirection());

        Booking booking = bookings.findByIdempotencyKey(key).orElseThrow();
        RentalOrder order = orders.findByBooking(booking).orElseThrow();
        assertEquals(bookingCount + 1, bookings.count());
        assertEquals(orderCount + 1, orders.count());
        assertEquals(customer.getId(), booking.getCustomer().getId());
        assertEquals(provider.getId(), booking.getProvider().getId());
        assertEquals(service.getId(), booking.getService().getId());
        assertEquals(customer.getId(), order.getCustomer().getId());
        assertEquals(provider.getId(), order.getProvider().getId());
        assertEquals(booking.getId(), order.getBooking().getId());
        assertEquals(1, orderItems.findByOrderIdOrderByIdAsc(order.getId()).size());
        RentalOrderItem item = orderItems.findByOrderIdOrderByIdAsc(order.getId()).get(0);
        assertEquals(service.getId(), item.getService().getId());
        assertEquals(booking.getId(), item.getBooking().getId());
        assertTrue(cartItems.findByCartAndServiceId(cart, service.getId()).isEmpty());
        assertTrue(history.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .anyMatch(entry -> entry.getPreviousStatus() == null
                        && OrderStatus.PENDING_PROVIDER_ACCEPTANCE.name().equals(entry.getNewStatus())));

        mvc.perform(post("/booking/provider/{id}", providerProfile.getId()).with(csrf())
                        .param("serviceId", service.getId().toString())
                        .param("cartItemId", cartItem.getId().toString())
                        .param("idempotencyKey", key)
                        .param("bookingDate", day)
                        .param("timeSlot", "10:00")
                        .param("durationHours", "2")
                        .param("directBooking", "true")
                        .param("note", "Yêu cầu trong kiểm thử tích hợp"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + order.getId() + "/progress"));
        assertEquals(bookingCount + 1, bookings.count());
        assertEquals(orderCount + 1, orders.count());

        mvc.perform(post("/booking/provider/{id}", providerProfile.getId()).with(csrf())
                        .param("serviceId", service.getId().toString())
                        .param("idempotencyKey", key)
                        .param("bookingDate", day)
                        .param("timeSlot", "12:00")
                        .param("durationHours", "2")
                        .param("directBooking", "true")
                        .param("note", "Nội dung khác"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/marketplace"))
                .andExpect(flash().attributeExists("error"));
        assertEquals(orderCount + 1, orders.count());

        mvc.perform(post("/provider/orders/{id}/accept", order.getId()).with(csrf())
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/orders/{id}/start-confirmation", order.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/provider/orders/{id}/start-confirmation", order.getId()).with(csrf())
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/provider/orders/{id}/complete", order.getId()).with(csrf())
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().is3xxRedirection());

        RentalOrder progressed = orders.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING_CUSTOMER_ACCEPTANCE, progressed.getStatus());
        assertEquals(BookingStatus.IN_PROGRESS, bookings.findById(booking.getId()).orElseThrow().getStatus());
        List<String> statuses = history.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .map(RentalOrderStatusHistory::getNewStatus).toList();
        assertTrue(statuses.contains(OrderStatus.PENDING_START_CONFIRMATION.name()));
        assertTrue(statuses.contains(OrderStatus.PROCESSING.name()));
        assertTrue(statuses.contains(OrderStatus.PENDING_CUSTOMER_ACCEPTANCE.name()));

        mvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("orders",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.hasProperty("id",
                                org.hamcrest.Matchers.is(order.getId())))));
        mvc.perform(get("/orders/{id}/progress", order.getId()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("order", org.hamcrest.Matchers.hasProperty("id",
                        org.hamcrest.Matchers.is(order.getId()))));

        mvc.perform(get("/admin/orders").with(user("admin@skillrental.vn").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("adminOrderRows"));
        mvc.perform(post("/admin/orders/{id}/intervene", order.getId())
                        .with(csrf()).with(user("admin@skillrental.vn").roles("ADMIN"))
                        .param("note", "Đã đối chiếu giao dịch trong kiểm thử tích hợp"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        List<RentalOrderStatusHistory> finalHistory = history.findByOrderIdOrderByCreatedAtAsc(order.getId());
        assertEquals(OrderStatus.PENDING_CUSTOMER_ACCEPTANCE.name(),
                orders.findById(order.getId()).orElseThrow().getStatus().name());
        assertTrue(finalHistory.stream().anyMatch(entry ->
                "Đã đối chiếu giao dịch trong kiểm thử tích hợp".equals(entry.getNote())));
        assertNotNull(finalHistory.get(finalHistory.size() - 1).getChangedBy());
    }

    @Test
    void simultaneousSubmissionsWithSameIdempotencyKeyCreateOneOrder() throws Exception {
        User customer = users.findByEmailIgnoreCase("customer@skillrental.vn").orElseThrow();
        User provider = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        ProviderProfile providerProfile = providers.findByUser(provider).orElseThrow();
        ServiceCatalog service = services.findByProviderAndStatusOrderByCreatedAtDesc(provider, ServiceStatus.PUBLISHED)
                .stream().findFirst().orElseThrow();
        String key = java.util.UUID.randomUUID().toString();
        String day = LocalDate.now().plusDays(10).toString();
        long bookingCount = bookings.count();
        long orderCount = orders.count();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> submitConcurrently(ready, start, customer, providerProfile, service, key, day));
            var second = executor.submit(() -> submitConcurrently(ready, start, customer, providerProfile, service, key, day));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both requests should be ready.");
            start.countDown();
            MvcResult firstResult = first.get(30, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(30, TimeUnit.SECONDS);

            assertEquals(302, firstResult.getResponse().getStatus());
            assertEquals(302, secondResult.getResponse().getStatus());
            assertEquals(firstResult.getResponse().getRedirectedUrl(), secondResult.getResponse().getRedirectedUrl());
            assertTrue(firstResult.getResponse().getRedirectedUrl().startsWith("/orders/"));
        } finally {
            executor.shutdownNow();
        }

        Booking persisted = bookings.findByIdempotencyKey(key).orElseThrow();
        assertNotNull(orders.findByBooking(persisted).orElseThrow());
        assertEquals(bookingCount + 1, bookings.count());
        assertEquals(orderCount + 1, orders.count());
    }

    private MvcResult submitConcurrently(CountDownLatch ready, CountDownLatch start, User customer,
                                        ProviderProfile providerProfile, ServiceCatalog service,
                                        String key, String day) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to submit the concurrent request.");
        }
        return mvc.perform(post("/booking/provider/{id}", providerProfile.getId()).with(csrf())
                        .with(user(customer.getEmail()).roles("CUSTOMER"))
                        .param("serviceId", service.getId().toString())
                        .param("idempotencyKey", key)
                        .param("bookingDate", day)
                        .param("timeSlot", "10:00")
                        .param("durationHours", "2")
                        .param("directBooking", "true")
                        .param("note", "Yêu cầu đồng thời"))
                .andReturn();
    }
}
