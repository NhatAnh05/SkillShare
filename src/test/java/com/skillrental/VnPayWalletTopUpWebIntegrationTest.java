package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.UserRepository;
import com.skillrental.repository.UserWalletRepository;
import com.skillrental.repository.WalletTopUpRepository;
import com.skillrental.service.VnPayWalletTopUpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "skillshare.vnpay.tmn-code=ITMERCHANT",
        "skillshare.vnpay.hash-secret=integration-test-secret"
})
@AutoConfigureMockMvc
class VnPayWalletTopUpWebIntegrationTest {
    private static final String SECRET = "integration-test-secret";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserWalletRepository wallets;
    @Autowired WalletTopUpRepository topUps;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired VnPayWalletTopUpService topUpService;

    private User customer;

    @BeforeEach
    void setUp() {
        customer = users.saveAndFlush(User.builder()
                .fullName("VNPay integration customer")
                .email("vnpay-" + java.util.UUID.randomUUID() + "@integration.test")
                .password(passwordEncoder.encode("test-password"))
                .role(Role.CUSTOMER)
                .emailVerified(true)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        wallets.saveAndFlush(UserWallet.builder().user(customer)
                .balance(new BigDecimal("20000.00"))
                .lockedAmount(new BigDecimal("5000.00")).build());
    }

    @Test
    void signedIpnAndBrowserReturnUseRealDatabaseStateAndOnlyIpnCreditsOnce() throws Exception {
        String paymentUrl = topUpService.createPayment(customer, new BigDecimal("100000"), "127.0.0.1");
        String ref = URLDecoder.decode(
                paymentUrl.substring(paymentUrl.indexOf("vnp_TxnRef=") + "vnp_TxnRef=".length())
                        .split("&", 2)[0],
                StandardCharsets.UTF_8);
        WalletTopUp topUp = topUps.findByTxnRef(ref).orElseThrow();

        Map<String, String> ipn = signedIpn(topUp, "00", "00", "135790");
        mvc.perform(withParams(get("/customer/wallet/vnpay/return"), ipn)
                        .with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/wallet"));
        assertEquals(new BigDecimal("20000.00"), wallets.findByUser(customer).orElseThrow().getBalance(),
                "A signed browser return must not credit the wallet.");

        mvc.perform(withParams(get("/customer/wallet/vnpay/ipn"), ipn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        mvc.perform(withParams(get("/customer/wallet/vnpay/ipn"), ipn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("02"));
        assertEquals("PAID", topUps.findByTxnRef(ref).orElseThrow().getStatus());
        assertEquals(new BigDecimal("120000.00"), wallets.findByUser(customer).orElseThrow().getBalance());
        assertEquals(new BigDecimal("5000.00"), wallets.findByUser(customer).orElseThrow().getLockedAmount());

        String failedUrl = topUpService.createPayment(customer, new BigDecimal("50000"), "127.0.0.1");
        String failedRef = URLDecoder.decode(
                failedUrl.substring(failedUrl.indexOf("vnp_TxnRef=") + "vnp_TxnRef=".length())
                        .split("&", 2)[0],
                StandardCharsets.UTF_8);
        WalletTopUp failedTopUp = topUps.findByTxnRef(failedRef).orElseThrow();
        Map<String, String> failedIpn = signedIpn(failedTopUp, "24", "02", "135791");
        mvc.perform(withParams(get("/customer/wallet/vnpay/ipn"), failedIpn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertEquals("FAILED", topUps.findByTxnRef(failedRef).orElseThrow().getStatus());
        assertEquals(new BigDecimal("120000.00"), wallets.findByUser(customer).orElseThrow().getBalance());

        Map<String, String> invalidSignature = signedIpn(failedTopUp, "00", "00", "135792");
        invalidSignature.put("vnp_Amount", "999999999");
        mvc.perform(withParams(get("/customer/wallet/vnpay/ipn"), invalidSignature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"));
        assertEquals(new BigDecimal("120000.00"), wallets.findByUser(customer).orElseThrow().getBalance());
    }

    private Map<String, String> signedIpn(WalletTopUp topUp, String responseCode,
                                          String transactionStatus, String gatewayNo) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_TxnRef", topUp.getTxnRef());
        params.put("vnp_TmnCode", "ITMERCHANT");
        params.put("vnp_Amount", topUp.getAmount().multiply(BigDecimal.valueOf(100))
                .toBigIntegerExact().toString());
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_TransactionNo", gatewayNo);
        params.put("vnp_BankCode", "NCB");
        sign(params);
        return params;
    }

    private MockHttpServletRequestBuilder withParams(MockHttpServletRequestBuilder request,
                                                      Map<String, String> params) {
        params.forEach(request::param);
        return request;
    }

    private void sign(Map<String, String> params) {
        try {
            StringBuilder data = new StringBuilder();
            for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
                if (!data.isEmpty()) data.append('&');
                data.append(java.net.URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            params.put("vnp_SecureHash", HexFormat.of().formatHex(
                    mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
