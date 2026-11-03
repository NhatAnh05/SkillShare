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
                .andExpect(status().isForbidden()); // A locked actor cannot use a previously authenticated session.
        User operator=users.saveAndFlush(User.builder().fullName("Active administrator").email("active-operator@test.local")
                .password("unused").role(com.skillrental.entity.Role.ADMIN).emailVerified(true).accountStatus(AccountStatus.ACTIVE).build());
        mvc.perform(post("/admin/users/{id}/unlock", admin.getId()).with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(operator.getEmail()).roles("ADMIN")))
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
    @Autowired private com.skillrental.repository.UserRoleChangeRepository roleChanges;
    @Autowired private com.skillrental.repository.RentalOrderRepository orders;
    @Autowired private com.skillrental.repository.CustomerProfileRepository customers;
    @Autowired private com.skillrental.repository.UserWalletRepository wallets;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired private com.skillrental.service.UserManagementService management;

    private User roleCandidate(com.skillrental.entity.Role role) {
        User u = users.saveAndFlush(User.builder().fullName("Role candidate " + role)
                .email(java.util.UUID.randomUUID()+"@roles.test").password(encoder.encode("RolePassword123!"))
                .role(role).accountStatus(AccountStatus.ACTIVE).emailVerified(true).build());
        if(role==com.skillrental.entity.Role.CUSTOMER)customers.saveAndFlush(com.skillrental.entity.CustomerProfile.builder().user(u).location("Test location").build());
        else providers.saveAndFlush(ProviderProfile.builder().user(u).headline("Provider profile").hourlyRate(java.math.BigDecimal.ZERO).rating(0.0).reviewCount(0).premium(false).approved(false).build());
        return u;
    }

    @Test
    @WithMockUser(username="admin@skillrental.vn",roles="ADMIN")
    void adminCanPromoteCustomerAndProviderAndDuplicateSubmitDoesNotDuplicateAudit() throws Exception {
        for(var role: new com.skillrental.entity.Role[]{com.skillrental.entity.Role.CUSTOMER,com.skillrental.entity.Role.PROVIDER}){
            User target=roleCandidate(role);
            mvc.perform(post("/admin/users/{id}/role",target.getId()).with(csrf()).param("role","ADMIN").param("reason","Appoint system administrator"))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/users")).andExpect(flash().attributeExists("success"));
            User saved=reload(target.getId());assertEquals(com.skillrental.entity.Role.ADMIN,saved.getRole());assertEquals(1,roleChanges.countByTargetId(target.getId()));
            if(role==com.skillrental.entity.Role.CUSTOMER)assertTrue(customers.findByUser(saved).isPresent());else assertTrue(providers.findByUser(saved).isPresent());
            mvc.perform(post("/admin/users/{id}/role",target.getId()).with(csrf()).param("role","ADMIN").param("reason","Repeated request"))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("info"));
            assertEquals(1,roleChanges.countByTargetId(target.getId()));
        }
        mvc.perform(get("/admin/users")).andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Lịch sử cấp quyền Admin")));
    }

    @Test
    void promotionRequiresAdminAtBothEndpointAndServiceAndRequiresCsrf() throws Exception {
        User target=roleCandidate(com.skillrental.entity.Role.CUSTOMER);
        mvc.perform(post("/admin/users/{id}/role",target.getId()).with(csrf()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("customer@skillrental.vn").roles("CUSTOMER")).param("role","ADMIN").param("reason","Try"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/{id}/role",target.getId()).with(csrf()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("customer@skillrental.vn").roles("ADMIN")).param("role","ADMIN").param("reason","Forged authority"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/{id}/role",target.getId()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@skillrental.vn").roles("ADMIN")).param("role","ADMIN").param("reason","No CSRF"))
                .andExpect(status().isForbidden());
        var customer=users.findByEmailIgnoreCase("customer@skillrental.vn").orElseThrow();
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,()->management.promoteToAdmin(target.getId(),customer,"ADMIN","Not an administrator"));
        assertEquals(com.skillrental.entity.Role.CUSTOMER,reload(target.getId()).getRole());assertEquals(0,roleChanges.countByTargetId(target.getId()));
    }

    @Test
    @WithMockUser(username="admin@skillrental.vn",roles="ADMIN")
    void promotionValidatesRoleReasonAndTargetStatus() throws Exception {
        User target=roleCandidate(com.skillrental.entity.Role.CUSTOMER);
        for(String role:new String[]{"CUSTOMER","PROVIDER","INVALID"})
            mvc.perform(post("/admin/users/{id}/role",target.getId()).with(csrf()).param("role",role).param("reason","Invalid target role")).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        mvc.perform(post("/admin/users/{id}/role",target.getId()).with(csrf()).param("role","ADMIN").param("reason"," ")).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        target.setAccountStatus(AccountStatus.LOCKED);users.saveAndFlush(target);
        mvc.perform(post("/admin/users/{id}/role",target.getId()).with(csrf()).param("role","ADMIN").param("reason","Locked")).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        target=reload(target.getId());target.setAccountStatus(AccountStatus.ACTIVE);target.setEmailVerified(false);users.saveAndFlush(target);
        mvc.perform(post("/admin/users/{id}/role",target.getId()).with(csrf()).param("role","ADMIN").param("reason","Not verified")).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertEquals(com.skillrental.entity.Role.CUSTOMER,reload(target.getId()).getRole());assertEquals(0,roleChanges.countByTargetId(target.getId()));
    }

    @Test
    @WithMockUser(username="admin@skillrental.vn",roles="ADMIN")
    void cannotAbandonAnActiveOrderOrProviderWalletByChangingRoles() throws Exception {
        User target=roleCandidate(com.skillrental.entity.Role.CUSTOMER);var provider=users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        orders.saveAndFlush(com.skillrental.entity.RentalOrder.builder().customer(target).provider(provider).subtotal(java.math.BigDecimal.TEN).discount(java.math.BigDecimal.ZERO).total(java.math.BigDecimal.TEN).status(com.skillrental.entity.OrderStatus.PROCESSING).build());
        mvc.perform(post("/admin/users/{id}/role",target.getId()).with(csrf()).param("role","ADMIN").param("reason","Has active work")).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertEquals(com.skillrental.entity.Role.CUSTOMER,reload(target.getId()).getRole());
        User candidate=roleCandidate(com.skillrental.entity.Role.PROVIDER);
        wallets.saveAndFlush(com.skillrental.entity.UserWallet.builder().user(candidate).balance(java.math.BigDecimal.TEN).build());
        mvc.perform(post("/admin/users/{id}/role",candidate.getId()).with(csrf()).param("role","ADMIN").param("reason","Has provider funds")).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertEquals(com.skillrental.entity.Role.PROVIDER,reload(candidate.getId()).getRole());
    }

    @Test
    void promotedAccountMustReauthenticateAndCanThenOpenAdminDashboard() throws Exception {
        User target=roleCandidate(com.skillrental.entity.Role.CUSTOMER);
        var login=mvc.perform(post("/login").with(csrf()).param("username",target.getEmail()).param("password","RolePassword123!")).andExpect(status().is3xxRedirection()).andReturn();
        var session=(org.springframework.mock.web.MockHttpSession)login.getRequest().getSession(false);
        mvc.perform(post("/admin/users/{id}/role",target.getId()).with(csrf()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@skillrental.vn").roles("ADMIN")).param("role","ADMIN").param("reason","New administrator")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/dashboard").session(session)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?roleChanged"));assertTrue(session.isInvalid());
        var again=mvc.perform(post("/login").with(csrf()).param("username",target.getEmail()).param("password","RolePassword123!")).andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(get("/admin/dashboard").session((org.springframework.mock.web.MockHttpSession)again.getRequest().getSession(false))).andExpect(status().isOk());
    }

}
