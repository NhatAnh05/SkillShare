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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.mail.username=", "spring.mail.password="})
@AutoConfigureMockMvc
class PasswordResetNoSmtpTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;

    @Test
    void missingMailConfigDoesNotCreateVisibleRecoveryLink() throws Exception {
        User user = users.saveAndFlush(User.builder().fullName("Mail Unconfigured")
                .email("smtp-missing-" + UUID.randomUUID() + "@example.test")
                .password(passwords.encode("old-password-123"))
                .role(Role.CUSTOMER).emailVerified(true).accountStatus(AccountStatus.ACTIVE).build());

        mvc.perform(post("/forgot-password").with(csrf()).param("email", user.getEmail()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attributeExists("error"));
        assertNull(users.findById(user.getId()).orElseThrow().getResetToken());
    }
}
