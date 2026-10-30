package com.skillrental;

import com.skillrental.entity.AccountStatus;
import com.skillrental.entity.ProviderProfile;
import com.skillrental.entity.ServiceCatalog;
import com.skillrental.entity.ServiceStatus;
import com.skillrental.entity.User;
import com.skillrental.repository.OfferedServiceRepository;
import com.skillrental.repository.ProviderProfileRepository;
import com.skillrental.repository.UserRepository;
import com.skillrental.workspace.WorkspaceController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminUserManagementTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProviderProfileRepository providers;

    @Autowired
    private OfferedServiceRepository services;

    @Test
    @WithMockUser(username = "admin@skillrental.vn", roles = "ADMIN")
    void adminCanLockPersistUnlockAndRestoreCustomerLogin() throws Exception {
        User customer = users.findByEmailIgnoreCase("customer@skillrental.vn").orElseThrow();

        mvc.perform(post("/admin/users/{id}/lock", customer.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        assertEquals(AccountStatus.LOCKED, reload(customer.getId()).getAccountStatus());

        MvcResult usersPage = mvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        var userRows = (java.util.List<com.skillrental.workspace.WorkspaceController.AdminUserRow>)
                usersPage.getModelAndView().getModel().get("userRows");
        assertTrue(userRows.stream().anyMatch(row -> row.user().getId().equals(customer.getId()) && row.blocked()));

        mvc.perform(post("/login").with(csrf())
                        .param("username", "customer@skillrental.vn")
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        mvc.perform(post("/admin/users/{id}/unlock", customer.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        assertEquals(AccountStatus.ACTIVE, reload(customer.getId()).getAccountStatus());

        mvc.perform(post("/login").with(csrf())
                        .param("username", "customer@skillrental.vn")
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/workspace"));
    }

    @Test
    @WithMockUser(username = "customer@skillrental.vn", roles = "CUSTOMER")
    void nonAdminCannotLockOrUnlockUsers() throws Exception {
        User provider = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();

        mvc.perform(post("/admin/users/{id}/lock", provider.getId()).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/{id}/unlock", provider.getId()).with(csrf()))
                .andExpect(status().isForbidden());

        assertEquals(AccountStatus.ACTIVE, reload(provider.getId()).getAccountStatus());
    }

    @Test
    @WithMockUser(username = "admin@skillrental.vn", roles = "ADMIN")
    void adminAccountCannotBeLockedOrUnlocked() throws Exception {
        User admin = users.findByEmailIgnoreCase("admin@skillrental.vn").orElseThrow();

        mvc.perform(post("/admin/users/{id}/lock", admin.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertEquals(AccountStatus.ACTIVE, reload(admin.getId()).getAccountStatus());

        admin.setAccountStatus(AccountStatus.LOCKED);
        users.saveAndFlush(admin);

        mvc.perform(post("/admin/users/{id}/unlock", admin.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertEquals(AccountStatus.LOCKED, reload(admin.getId()).getAccountStatus());
    }

    @Test
    @WithMockUser(username = "admin@skillrental.vn", roles = "ADMIN")
    void missingUserAndRepeatedTransitionsAreHandledWithoutChangingState() throws Exception {
        mvc.perform(post("/admin/users/{id}/lock", 999999L).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        User provider = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        mvc.perform(post("/admin/users/{id}/lock", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(AccountStatus.LOCKED, reload(provider.getId()).getAccountStatus());

        mvc.perform(post("/admin/users/{id}/lock", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertEquals(AccountStatus.LOCKED, reload(provider.getId()).getAccountStatus());

        mvc.perform(post("/admin/users/{id}/unlock", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(AccountStatus.ACTIVE, reload(provider.getId()).getAccountStatus());

        mvc.perform(post("/admin/users/{id}/unlock", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertEquals(AccountStatus.ACTIVE, reload(provider.getId()).getAccountStatus());
    }

    @Test
    @WithMockUser(username = "admin@skillrental.vn", roles = "ADMIN")
    void lockedUnverifiedUserReturnsToPendingVerificationWhenUnlocked() throws Exception {
        User customer = users.findByEmailIgnoreCase("customer@skillrental.vn").orElseThrow();
        customer.setEmailVerified(false);
        users.saveAndFlush(customer);

        mvc.perform(post("/admin/users/{id}/lock", customer.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/users/{id}/unlock", customer.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertEquals(AccountStatus.PENDING_VERIFICATION, reload(customer.getId()).getAccountStatus());
        assertFalse(reload(customer.getId()).getEmailVerified());
    }

    @Test
    @WithMockUser(username = "admin@skillrental.vn", roles = "ADMIN")
    void providerUnlockRestoresMarketplaceVisibilityWithoutChangingApprovalOrPublishedServices() throws Exception {
        User provider = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        ProviderProfile profile = providers.findByUser(provider).orElseThrow();
        var publishedBefore = services.findByProviderAndStatusOrderByCreatedAtDesc(provider, ServiceStatus.PUBLISHED)
                .stream().map(ServiceCatalog::getId).toList();
        assertFalse(publishedBefore.isEmpty());

        mvc.perform(post("/admin/users/{id}/lock", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        MvcResult adminPage = mvc.perform(get("/admin/providers"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        var rows = (java.util.List<WorkspaceController.AdminProviderRow>)
                adminPage.getModelAndView().getModel().get("providerRows");
        var lockedRow = rows.stream().filter(row -> row.profile().getId().equals(profile.getId()))
                .findFirst().orElseThrow();
        assertEquals(AccountStatus.LOCKED, lockedRow.profile().getUser().getAccountStatus());
        assertFalse(lockedRow.marketplaceVisible());
        assertTrue(lockedRow.marketplaceReason().contains("LOCKED"));

        mvc.perform(post("/admin/users/{id}/unlock", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        User unlocked = reload(provider.getId());
        ProviderProfile unchangedProfile = providers.findByUser(unlocked).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, unlocked.getAccountStatus());
        assertTrue(unchangedProfile.getApproved());
        assertEquals("ACTIVE", unchangedProfile.getProfileStatus());
        assertEquals(publishedBefore,
                services.findByProviderAndStatusOrderByCreatedAtDesc(unlocked, ServiceStatus.PUBLISHED)
                        .stream().map(ServiceCatalog::getId).toList());

        MvcResult restoredAdminPage = mvc.perform(get("/admin/providers"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        var restoredRows = (java.util.List<WorkspaceController.AdminProviderRow>)
                restoredAdminPage.getModelAndView().getModel().get("providerRows");
        var restoredRow = restoredRows.stream().filter(row -> row.profile().getId().equals(profile.getId()))
                .findFirst().orElseThrow();
        assertTrue(restoredRow.marketplaceVisible());
        assertEquals(publishedBefore.size(), restoredRow.publishedServiceCount());

        MvcResult providersPage = mvc.perform(get("/providers"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        var publicProviders = (java.util.List<ProviderProfile>)
                providersPage.getModelAndView().getModel().get("providers");
        assertTrue(publicProviders.stream().anyMatch(item -> item.getId().equals(profile.getId())));

        MvcResult servicesPage = mvc.perform(get("/services"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        var publicServices = (java.util.List<ServiceCatalog>)
                servicesPage.getModelAndView().getModel().get("services");
        assertTrue(publicServices.stream().map(ServiceCatalog::getId).toList().containsAll(publishedBefore));

        mvc.perform(post("/admin/users/{id}/lock", provider.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(AccountStatus.LOCKED, reload(provider.getId()).getAccountStatus());
    }

    private User reload(Long userId) {
        return users.findById(userId).orElseThrow();
    }
}
