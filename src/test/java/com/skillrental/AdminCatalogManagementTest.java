package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.BookingRepository;
import com.skillrental.repository.OfferedServiceRepository;
import com.skillrental.repository.ProviderProfileRepository;
import com.skillrental.repository.RentalOrderRepository;
import com.skillrental.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminCatalogManagementTest {
    @Autowired private MockMvc mvc;
    @Autowired private OfferedServiceRepository services;
    @Autowired private ProviderProfileRepository providers;
    @Autowired private UserRepository users;
    @Autowired private BookingRepository bookings;
    @Autowired private RentalOrderRepository orders;

    @Test
    @WithMockUser(username = "admin@skillrental.vn", roles = "ADMIN")
    void adminPublishesDraftServiceAndAdminListsReflectDatabase() throws Exception {
        User provider = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        ServiceCatalog draft = saveDraft(provider);

        mvc.perform(post("/admin/services/{id}/approve", draft.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/pending-services"));

        ServiceCatalog persisted = services.findById(draft.getId()).orElseThrow();
        assertEquals(ServiceStatus.PUBLISHED, persisted.getStatus());
        assertNotNull(persisted.getPublishedAt());

        mvc.perform(get("/admin/services"))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/admin/page"))
                .andExpect(model().attribute("serviceRows",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(draft.getId())))));

        mvc.perform(get("/admin/pending-services"))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/admin/page"))
                .andExpect(model().attribute("pendingServices",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(draft.getId()))))));
    }

    @Test
    @WithMockUser(username = "admin@skillrental.vn", roles = "ADMIN")
    void providerApprovalSuspensionAndReactivationPersistAndGateBooking() throws Exception {
        ProviderProfile provider = providers.findByUser(
                users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow()).orElseThrow();
        provider.setApproved(false);
        providers.saveAndFlush(provider);
        ServiceCatalog service = services.findByStatusOrderByRatingDesc(ServiceStatus.PUBLISHED).stream()
                .filter(item -> item.getProvider().getId().equals(provider.getUser().getId()))
                .findFirst().orElseThrow();

        mvc.perform(post("/admin/providers/{id}/approve", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(providers.findById(provider.getId()).orElseThrow().getApproved());

        mvc.perform(post("/admin/providers/{id}/suspend", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals("SUSPENDED", providers.findById(provider.getId()).orElseThrow().getProfileStatus());
        var providerSearch = mvc.perform(get("/providers"))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("providers");
        assertTrue(providerSearch instanceof java.util.List<?> list
                && list.stream().noneMatch(item -> ((ProviderProfile) item).getId().equals(provider.getId())));

        long ordersBefore = orders.count();
        mvc.perform(post("/booking/provider/{id}", provider.getId()).with(csrf())
                        .with(user("customer@skillrental.vn").roles("CUSTOMER"))
                        .param("serviceId", service.getId().toString())
                        .param("bookingDate", LocalDate.now().plusDays(10).toString())
                        .param("timeSlot", "10:00")
                        .param("durationHours", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/marketplace"));
        assertEquals(ordersBefore, orders.count());

        mvc.perform(post("/admin/providers/{id}/reactivate", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals("ACTIVE", providers.findById(provider.getId()).orElseThrow().getProfileStatus());

        mvc.perform(post("/booking/provider/{id}", provider.getId()).with(csrf())
                        .with(user("customer@skillrental.vn").roles("CUSTOMER"))
                        .param("serviceId", service.getId().toString())
                        .param("bookingDate", LocalDate.now().plusDays(10).toString())
                        .param("timeSlot", "10:00")
                        .param("durationHours", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern("/orders/*/progress"));

        Booking booking = bookings.findAll().stream()
                .max(java.util.Comparator.comparing(Booking::getId)).orElseThrow();
        assertEquals(service.getName(), booking.getServiceName());
        assertEquals(provider.getHourlyRate().multiply(BigDecimal.valueOf(2)), booking.getAmount());
    }

    @Test
    @WithMockUser(username = "customer@skillrental.vn", roles = "CUSTOMER")
    void nonAdminCannotChangeProviderOrServiceApprovalAndStatusesRemainUnchanged() throws Exception {
        User providerUser = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        ProviderProfile provider = providers.findByUser(providerUser).orElseThrow();
        ServiceCatalog draft = saveDraft(providerUser);
        provider.setApproved(false);
        providers.saveAndFlush(provider);

        mvc.perform(post("/admin/services/{id}/approve", draft.getId()).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/providers/{id}/approve", provider.getId()).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/providers/{id}/suspend", provider.getId()).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/providers/{id}/reactivate", provider.getId()).with(csrf()))
                .andExpect(status().isForbidden());

        assertEquals(ServiceStatus.DRAFT, services.findById(draft.getId()).orElseThrow().getStatus());
        assertEquals(false, providers.findById(provider.getId()).orElseThrow().getApproved());
        assertEquals("ACTIVE", providers.findById(provider.getId()).orElseThrow().getProfileStatus());
    }

    @Test
    @WithMockUser(username = "admin@skillrental.vn", roles = "ADMIN")
    void missingIdsAndInvalidRepeatedTransitionsAreReportedWithoutStateChanges() throws Exception {
        mvc.perform(post("/admin/services/{id}/approve", 999999L).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/admin/providers/{id}/approve", 999999L).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/admin/providers/{id}/suspend", 999999L).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        User providerUser = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        ProviderProfile provider = providers.findByUser(providerUser).orElseThrow();
        ServiceCatalog draft = saveDraft(providerUser);

        mvc.perform(post("/admin/services/{id}/approve", draft.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/services/{id}/approve", draft.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertEquals(ServiceStatus.PUBLISHED, services.findById(draft.getId()).orElseThrow().getStatus());

        mvc.perform(post("/admin/providers/{id}/suspend", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/providers/{id}/suspend", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertEquals("SUSPENDED", providers.findById(provider.getId()).orElseThrow().getProfileStatus());

        mvc.perform(post("/admin/providers/{id}/reactivate", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/providers/{id}/reactivate", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertEquals("ACTIVE", providers.findById(provider.getId()).orElseThrow().getProfileStatus());
    }

    @Test
    @WithMockUser(username = "customer@skillrental.vn", roles = "CUSTOMER")
    void bookingRejectsDraftPausedAndArchivedServices() throws Exception {
        User providerUser = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        ProviderProfile provider = providers.findByUser(providerUser).orElseThrow();
        ServiceCatalog service = services.findByStatusOrderByRatingDesc(ServiceStatus.PUBLISHED).stream()
                .filter(item -> item.getProvider().getId().equals(providerUser.getId()))
                .findFirst().orElseThrow();
        provider.setApproved(false);
        providers.saveAndFlush(provider);
        long ordersBefore = orders.count();

        mvc.perform(post("/booking/provider/{id}", provider.getId()).with(csrf())
                        .param("serviceId", service.getId().toString())
                        .param("bookingDate", LocalDate.now().plusDays(10).toString())
                        .param("timeSlot", "10:00")
                        .param("durationHours", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/marketplace"));
        assertEquals(ordersBefore, orders.count());

        for (ServiceStatus unavailable : new ServiceStatus[]{ServiceStatus.DRAFT, ServiceStatus.PAUSED, ServiceStatus.ARCHIVED}) {
            service.setStatus(unavailable);
            services.saveAndFlush(service);
            mvc.perform(post("/booking/provider/{id}", provider.getId()).with(csrf())
                            .param("serviceId", service.getId().toString())
                            .param("bookingDate", LocalDate.now().plusDays(10).toString())
                            .param("timeSlot", "10:00")
                            .param("durationHours", "2"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/marketplace"));
            assertEquals(ordersBefore, orders.count());
        }
    }

    private ServiceCatalog saveDraft(User provider) {
        return services.saveAndFlush(ServiceCatalog.builder()
                .provider(provider)
                .name("Test draft service")
                .category("Testing")
                .description("Draft used by the Admin catalog integration test.")
                .pricingType(ServicePricingType.FIXED)
                .price(new BigDecimal("123456.00"))
                .status(ServiceStatus.DRAFT)
                .build());
    }
}
