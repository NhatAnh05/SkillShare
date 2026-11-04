package com.skillrental;

import com.skillrental.entity.AccountStatus;
import com.skillrental.entity.Role;
import com.skillrental.entity.User;
import com.skillrental.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A stale authenticated session must never retain a role that changed in the database. */
@SpringBootTest
@AutoConfigureMockMvc
class SessionRoleSecurityTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    private User customer(AccountStatus status) {
        return users.saveAndFlush(User.builder()
                .fullName("Role Verification")
                .email("session-" + UUID.randomUUID() + "@example.test")
                .password(encoder.encode("local-password-123"))
                .role(Role.CUSTOMER).accountStatus(status).emailVerified(true).build());
    }

    @Test
    void oldAdminSessionIsLoggedOutAfterDemotionToCustomer() throws Exception {
        User demoted = customer(AccountStatus.ACTIVE);
        mvc.perform(get("/admin/orders").with(user(demoted.getEmail()).roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?roleChanged"));
    }

    @Test
    void lockedCustomerCannotKeepAnOldSignedInSession() throws Exception {
        User locked = customer(AccountStatus.LOCKED);
        mvc.perform(get("/workspace").with(user(locked.getEmail()).roles("CUSTOMER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?roleChanged"));
    }
}
