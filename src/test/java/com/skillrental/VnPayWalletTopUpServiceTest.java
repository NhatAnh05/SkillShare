package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.service.VnPayWalletTopUpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VnPayWalletTopUpServiceTest {
    private static final String SECRET = "test-secret-not-for-production";
    private static final String MERCHANT = "TESTCODE";

    @Mock private WalletTopUpRepository topUps;
    @Mock private UserWalletRepository wallets;
    @Mock private WalletTransactionRepository transactions;
    private VnPayWalletTopUpService service;
    private User customer;
    private UserWallet wallet;
    private WalletTopUp topUp;

    @BeforeEach
    void setUp() {
        service = new VnPayWalletTopUpService(topUps, wallets, transactions);
        ReflectionTestUtils.setField(service, "tmnCode", MERCHANT);
        ReflectionTestUtils.setField(service, "hashSecret", SECRET);
        ReflectionTestUtils.setField(service, "payUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(service, "returnUrl", "http://localhost:8080/customer/wallet/vnpay/return");
        customer = User.builder().id(17L).email("test@example.org").role(Role.CUSTOMER).build();
        wallet = UserWallet.builder().id(2L).user(customer).balance(new BigDecimal("20000.00"))
                .lockedAmount(new BigDecimal("5000.00")).build();
        topUp = WalletTopUp.builder().id(9L).user(customer).txnRef("WS_TEST_001")
                .amount(new BigDecimal("100000.00")).status("PENDING").build();
    }

    @Test
    void paymentRequestRejectsFractionalVndBeforePersisting() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createPayment(customer, new BigDecimal("10000.25"), "127.0.0.1"));
        verifyNoInteractions(topUps);
    }

    @Test
    void paymentRequestCreatesPendingRecordAndSignedUrl() {
        when(wallets.findByUser(customer)).thenReturn(Optional.of(wallet));
        when(topUps.save(any(WalletTopUp.class))).thenAnswer(inv -> inv.getArgument(0));
        String url = service.createPayment(customer, new BigDecimal("100000.00"), "127.0.0.1");
        assertTrue(url.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertTrue(url.contains("vnp_Amount=10000000"));
        assertTrue(url.contains("vnp_SecureHash="));
        verify(topUps).save(argThat(t -> "PENDING".equals(t.getStatus())
                && new BigDecimal("100000").compareTo(t.getAmount()) == 0));
    }

    @Test
    void invalidSignatureCannotCreditWallet() {
        var params = validIpn();
        params.put("vnp_Amount", "20000000"); // tampers signed data
        assertEquals("97", service.processIpn(params).code());
        verifyNoInteractions(wallets, transactions, topUps);
    }

    @Test
    void wrongMerchantRejectedEvenWithValidSignature() {
        var params = validIpn();
        params.put("vnp_TmnCode", "OTHER123");
        sign(params);
        assertEquals("99", service.processIpn(params).code());
        verifyNoInteractions(wallets, transactions, topUps);
    }

    @Test
    void signedWrongAmountDoesNotChangeWallet() {
        when(topUps.findByTxnRefForUpdate("WS_TEST_001")).thenReturn(Optional.of(topUp));
        var params = validIpn();
        params.put("vnp_Amount", "9000000");
        sign(params);
        assertEquals("04", service.processIpn(params).code());
        verifyNoInteractions(wallets, transactions);
        assertEquals("PENDING", topUp.getStatus());
    }

    @Test
    void signedIpnCreditsOnceAndDuplicateIpnDoesNotCreditAgain() {
        when(topUps.findByTxnRefForUpdate("WS_TEST_001")).thenReturn(Optional.of(topUp));
        when(wallets.findByUserForUpdate(customer)).thenReturn(Optional.of(wallet));
        var params = validIpn();
        assertEquals("00", service.processIpn(params).code());
        assertEquals("PAID", topUp.getStatus());
        assertEquals(new BigDecimal("120000.00"), wallet.getBalance());
        assertEquals(new BigDecimal("5000.00"), wallet.getLockedAmount());
        assertEquals("02", service.processIpn(params).code());
        verify(transactions, times(1)).save(argThat(t -> "VNPAY_TOPUP".equals(t.getType())
                && t.getAmount().compareTo(new BigDecimal("100000")) == 0
                && t.getBalanceAfter().compareTo(new BigDecimal("120000")) == 0));
    }

    @Test
    void failedPaymentDoesNotCreditAndCannotLaterBeReplayedAsSuccess() {
        when(topUps.findByTxnRefForUpdate("WS_TEST_001")).thenReturn(Optional.of(topUp));
        var params = validIpn();
        params.put("vnp_ResponseCode", "24");
        params.put("vnp_TransactionStatus", "02");
        sign(params);
        assertEquals("00", service.processIpn(params).code());
        assertEquals("FAILED", topUp.getStatus());
        assertEquals(new BigDecimal("20000.00"), wallet.getBalance());
        assertEquals("02", service.processIpn(validIpn()).code());
        verifyNoInteractions(wallets, transactions);
    }

    @Test
    void reusedGatewayTransactionNumberIsRejected() {
        when(topUps.findByTxnRefForUpdate("WS_TEST_001")).thenReturn(Optional.of(topUp));
        when(topUps.existsByGatewayTransactionNo("135790")) .thenReturn(true);
        assertEquals("99", service.processIpn(validIpn()).code());
        verifyNoInteractions(wallets, transactions);
    }

    @Test
    void browserReturnOnlyReadsRecordedState() {
        topUp.setStatus("PENDING");
        when(topUps.findByTxnRef("WS_TEST_001")).thenReturn(Optional.of(topUp));
        assertEquals("PENDING", service.statusForReturn(validIpn(), customer));
        topUp.setStatus("PAID");
        assertEquals("PAID", service.statusForReturn(validIpn(), customer));
        var stranger = User.builder().id(55L).role(Role.CUSTOMER).build();
        assertEquals("INVALID", service.statusForReturn(validIpn(), stranger));
        verifyNoInteractions(wallets, transactions);
    }

    private Map<String, String> validIpn() {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_TxnRef", "WS_TEST_001");
        params.put("vnp_TmnCode", MERCHANT);
        params.put("vnp_Amount", "10000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "135790");
        params.put("vnp_BankCode", "NCB");
        sign(params);
        return params;
    }

    private void sign(Map<String, String> params) {
        try {
            Map<String, String> sorted = new TreeMap<>(params);
            sorted.remove("vnp_SecureHash");
            StringJoiner data = new StringJoiner("&");
            sorted.forEach((key, value) -> data.add(URLEncoder.encode(key, StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            params.put("vnp_SecureHash", HexFormat.of().formatHex(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
