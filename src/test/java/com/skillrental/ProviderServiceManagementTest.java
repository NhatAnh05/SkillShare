package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.BookingRepository;
import com.skillrental.repository.CartItemRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
class ProviderServiceManagementTest {
    @Autowired private MockMvc mvc;
    @Autowired private OfferedServiceRepository services;
    @Autowired private UserRepository users;
    @Autowired private CartItemRepository cartItems;
    @Autowired private ProviderProfileRepository providers;
    @Autowired private RentalOrderRepository orders;

    @Test
    @WithMockUser(username = "provider1@skillrental.vn", roles = "PROVIDER")
    void providerCreatesDraftServiceAndReadsItFromRepositoryAndWorkspace() throws Exception {
        User provider = provider();
        mvc.perform(post("/provider/services/new").with(csrf())
                        .param("name", "Gói kiểm thử mới")
                        .param("category", "Thiết kế")
                        .param("description", "Quyền lợi và phạm vi gói dịch vụ.")
                        .param("pricingType", "FIXED")
                        .param("price", "1250000.00")
                        .param("durationMinutes", "90")
                        .param("coverImageUrl", "https://example.test/cover.png"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/services"))
                .andExpect(flash().attributeExists("success"));

        ServiceCatalog saved = services.findByProviderOrderByCreatedAtDesc(provider).stream()
                .filter(item -> item.getName().equals("Gói kiểm thử mới"))
                .findFirst().orElseThrow();
        assertEquals(ServiceStatus.DRAFT, saved.getStatus());
        assertEquals(new BigDecimal("1250000.00"), saved.getPrice());
        assertNotNull(saved.getCreatedAt());

        mvc.perform(get("/provider/services"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("providerServices",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.hasProperty("id",
                                org.hamcrest.Matchers.is(saved.getId())))));

        mvc.perform(get("/admin/services").with(user("admin@skillrental.vn").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("serviceRows",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.hasProperty("id",
                                org.hamcrest.Matchers.is(saved.getId())))));
    }

    @Test
    @WithMockUser(username = "provider1@skillrental.vn", roles = "PROVIDER")
    void providerEditsOwnServiceAndPersistsFieldsWithoutChangingStatus() throws Exception {
        User provider = provider();
        ServiceCatalog service = saveService(provider, ServiceStatus.PUBLISHED);

        mvc.perform(post("/provider/services/{id}/edit", service.getId()).with(csrf())
                        .param("name", "Dịch vụ đã cập nhật")
                        .param("category", "Lập trình")
                        .param("description", "Mô tả quyền lợi đã được cập nhật.")
                        .param("pricingType", "HOURLY")
                        .param("price", "875000.50")
                        .param("durationMinutes", "120")
                        .param("coverImageUrl", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        ServiceCatalog persisted = services.findById(service.getId()).orElseThrow();
        assertEquals("Dịch vụ đã cập nhật", persisted.getName());
        assertEquals("Lập trình", persisted.getCategory());
        assertEquals("Mô tả quyền lợi đã được cập nhật.", persisted.getDescription());
        assertEquals(ServicePricingType.HOURLY, persisted.getPricingType());
        assertEquals(new BigDecimal("875000.50"), persisted.getPrice());
        assertEquals(120, persisted.getDurationMinutes());
        assertEquals(ServiceStatus.PUBLISHED, persisted.getStatus());
    }

    @Test
    @WithMockUser(username = "provider1@skillrental.vn", roles = "PROVIDER")
    void invalidInputMissingIdAndArchivedEditAreRejectedWithoutChangingData() throws Exception {
        User provider = provider();
        ServiceCatalog existing = saveService(provider, ServiceStatus.DRAFT);
        long countBefore = services.count();

        mvc.perform(post("/provider/services/new").with(csrf())
                        .param("name", " ")
                        .param("description", "Mô tả")
                        .param("pricingType", "FIXED")
                        .param("price", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        mvc.perform(post("/provider/services/{id}/edit", 999999L).with(csrf())
                        .param("name", "Không tồn tại")
                        .param("description", "Mô tả")
                        .param("pricingType", "FIXED")
                        .param("price", "100"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        mvc.perform(post("/provider/services/{id}/archive", existing.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));
        assertEquals(ServiceStatus.ARCHIVED, services.findById(existing.getId()).orElseThrow().getStatus());
        mvc.perform(post("/provider/services/{id}/edit", existing.getId()).with(csrf())
                        .param("name", "Không được thay đổi")
                        .param("description", "Mô tả")
                        .param("pricingType", "FIXED")
                        .param("price", "100"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertEquals(countBefore, services.count());
        assertEquals("Test service", services.findById(existing.getId()).orElseThrow().getName());
        assertEquals(ServiceStatus.ARCHIVED, services.findById(existing.getId()).orElseThrow().getStatus());
    }

    @Test
    @WithMockUser(username = "provider1@skillrental.vn", roles = "PROVIDER")
    void providerCannotEditAnotherProvidersService() throws Exception {
        User otherProvider = users.saveAndFlush(User.builder()
                .fullName("Another provider")
                .email("another-provider@skillrental.test")
                .password("not-a-real-login-password")
                .role(Role.PROVIDER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build());
        ServiceCatalog service = saveService(otherProvider, ServiceStatus.DRAFT);

        mvc.perform(post("/provider/services/{id}/edit", service.getId()).with(csrf())
                        .param("name", "Unauthorized change")
                        .param("description", "Mô tả không được lưu.")
                        .param("pricingType", "FIXED")
                        .param("price", "500"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertEquals("Test service", services.findById(service.getId()).orElseThrow().getName());
    }

    @Test
    @WithMockUser(username = "provider1@skillrental.vn", roles = "PROVIDER")
    void onlyValidVisibilityTransitionsPersistAndUpdateMarketplaceAndCartEligibility() throws Exception {
        User provider = provider();
        ServiceCatalog draft = saveService(provider, ServiceStatus.DRAFT);
        ServiceCatalog published = saveService(provider, ServiceStatus.PUBLISHED);
        long cartItemsBefore = cartItems.count();
        long ordersBefore = orders.count();

        mvc.perform(post("/provider/services/{id}/pause", draft.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertEquals(ServiceStatus.DRAFT, services.findById(draft.getId()).orElseThrow().getStatus());

        mvc.perform(get("/services").with(user("customer@skillrental.vn").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("services",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.hasProperty("id",
                                org.hamcrest.Matchers.is(published.getId())))));

        mvc.perform(post("/provider/services/{id}/pause", published.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));
        assertEquals(ServiceStatus.PAUSED, services.findById(published.getId()).orElseThrow().getStatus());
        ProviderProfile providerProfile = providers.findByUser(provider).orElseThrow();
        mvc.perform(post("/booking/provider/{id}", providerProfile.getId()).with(csrf())
                        .with(user("customer@skillrental.vn").roles("CUSTOMER"))
                        .param("serviceId", published.getId().toString())
                        .param("bookingDate", LocalDate.now().plusDays(10).toString())
                        .param("timeSlot", "10:00")
                        .param("idempotencyKey", java.util.UUID.randomUUID().toString())
                        .param("durationHours", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/marketplace"));
        assertEquals(ordersBefore, orders.count());

        mvc.perform(get("/services").with(user("customer@skillrental.vn").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("services",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.hasProperty("id",
                                        org.hamcrest.Matchers.is(published.getId()))))));
        mvc.perform(post("/cart/items").with(csrf())
                        .with(user("customer@skillrental.vn").roles("CUSTOMER"))
                        .param("serviceId", published.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/services"));
        assertEquals(cartItemsBefore, cartItems.count());

        mvc.perform(post("/provider/services/{id}/resume", published.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));
        assertEquals(ServiceStatus.PUBLISHED, services.findById(published.getId()).orElseThrow().getStatus());

        mvc.perform(post("/provider/services/{id}/resume", published.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertEquals(ServiceStatus.PUBLISHED, services.findById(published.getId()).orElseThrow().getStatus());

        mvc.perform(post("/cart/items").with(csrf())
                        .with(user("customer@skillrental.vn").roles("CUSTOMER"))
                        .param("serviceId", published.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));
        assertEquals(cartItemsBefore + 1, cartItems.count());
    }

    @Test
    @WithMockUser(username = "customer@skillrental.vn", roles = "CUSTOMER")
    void nonProviderCannotCreateOrManageProviderServices() throws Exception {
        mvc.perform(post("/provider/services/new").with(csrf())
                        .param("name", "Không được tạo")
                        .param("description", "Mô tả")
                        .param("pricingType", "FIXED")
                        .param("price", "100"))
                .andExpect(status().isForbidden());
    }

    private User provider() {
        return users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
    }

    private ServiceCatalog saveService(User provider, ServiceStatus status) {
        return services.saveAndFlush(ServiceCatalog.builder()
                .provider(provider)
                .name("Test service")
                .category("Test")
                .description("Service used in provider management tests.")
                .pricingType(ServicePricingType.FIXED)
                .price(new BigDecimal("1000.00"))
                .durationMinutes(60)
                .status(status)
                .build());
    }
}
