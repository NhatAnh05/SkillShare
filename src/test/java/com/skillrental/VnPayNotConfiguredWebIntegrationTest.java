package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.UserRepository;
import com.skillrental.repository.UserWalletRepository;
import com.skillrental.repository.WalletTopUpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "skillshare.vnpay.tmn-code=",
        "skillshare.vnpay.hash-secret="
})
@AutoConfigureMockMvc
class VnPayNotConfiguredWebIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserWalletRepository wallets;
    @Autowired WalletTopUpRepository topUps;
    @Autowired PasswordEncoder passwordEncoder;

    private User customer;

    @BeforeEach
    void setUp() {
        customer = users.saveAndFlush(User.builder()
                .fullName("Unconfigured VNPay customer")
                .email("unconfigured-" + java.util.UUID.randomUUID() + "@integration.test")
                .password(passwordEncoder.encode("test-password"))
                .role(Role.CUSTOMER)
                .emailVerified(true)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        wallets.saveAndFlush(UserWallet.builder().user(customer)
                .balance(new BigDecimal("20000.00"))
                .lockedAmount(BigDecimal.ZERO).build());
    }

    @Test
    void walletExplainsMissingMerchantAndRejectsTopUpWithoutCreatingPayment() throws Exception {
        mvc.perform(get("/customer/wallet").with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chưa cấu hình VNPay Sandbox")));

        mvc.perform(post("/customer/wallet/topup-vnpay")
                        .with(user(customer.getEmail()).roles("CUSTOMER"))
                        .with(csrf())
                        .param("amount", "100000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/wallet"))
                .andExpect(flash().attributeExists("error"));

        assertTrue(topUps.findTop10ByUserOrderByCreatedAtDesc(customer).isEmpty());
        assertEquals(new BigDecimal("20000.00"), wallets.findByUser(customer).orElseThrow().getBalance());
    }
}
