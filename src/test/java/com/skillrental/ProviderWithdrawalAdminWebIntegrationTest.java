package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.ProviderWithdrawalRepository;
import com.skillrental.repository.UserRepository;
import com.skillrental.repository.UserWalletRepository;
import com.skillrental.repository.WalletTransactionRepository;
import com.skillrental.service.EscrowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "skillshare.escrow.show-demo-otp=true")
@AutoConfigureMockMvc
class ProviderWithdrawalAdminWebIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserWalletRepository wallets;
    @Autowired WalletTransactionRepository transactions;
    @Autowired ProviderWithdrawalRepository withdrawals;
    @Autowired EscrowService escrow;
    @Autowired PasswordEncoder passwordEncoder;

    private User provider;
    private User admin;

    @BeforeEach
    void setUp() {
        provider = createUser(Role.PROVIDER);
        admin = createUser(Role.ADMIN);
        wallets.saveAndFlush(UserWallet.builder().user(provider)
                .balance(new BigDecimal("3000000.00"))
                .lockedAmount(BigDecimal.ZERO).build());
    }

    @Test
    void adminApprovalIsAuditedAndExplicitlyDoesNotCreateBankTransfer() throws Exception {
        EscrowService.WithdrawalOtpDispatchResult request = escrow.createWithdrawal(provider,
                new BigDecimal("2000000"), "Test Bank", "12345678", "Test Provider");
        assertNotNull(request.demoCode());
        escrow.verifyWithdrawalOtp(request.withdrawalId(), provider, request.demoCode());

        mvc.perform(get("/admin/finance").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Yêu cầu rút tiền từ Provider")));
        mvc.perform(post("/admin/withdrawals/{id}/approve", request.withdrawalId())
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/finance"));

        ProviderWithdrawal approved = withdrawals.findById(request.withdrawalId()).orElseThrow();
        assertEquals("APPROVED_DEMO", approved.getStatus());
        assertEquals(admin.getId(), approved.getProcessedByAdmin().getId());
        assertNotNull(approved.getVerifiedAt());
        assertNotNull(approved.getProcessedAt());
        assertTrue(approved.getAdminNote().contains("chưa phát sinh giao dịch chuyển khoản"));
        assertEquals(1, transactions.findByWalletOrderByCreatedAtDesc(wallets.findByUser(provider).orElseThrow())
                .stream().filter(t -> "WITHDRAWAL_PENDING".equals(t.getType())).count());
        assertFalse(transactions.findByWalletOrderByCreatedAtDesc(wallets.findByUser(provider).orElseThrow())
                .stream().anyMatch(t -> t.getType().contains("BANK_TRANSFER")));
        mvc.perform(get("/admin/finance").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(admin.getFullName())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("chưa phát sinh giao dịch chuyển khoản")));
    }

    private User createUser(Role role) {
        return users.saveAndFlush(User.builder()
                .fullName("Withdrawal audit " + role)
                .email(UUID.randomUUID() + "@withdrawal-audit.test")
                .password(passwordEncoder.encode("test-password"))
                .role(role)
                .emailVerified(true)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }
}
