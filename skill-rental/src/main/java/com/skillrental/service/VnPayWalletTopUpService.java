package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Nạp ví VNPay: chỉ IPN đã xác thực được phép ghi sổ. Return URL là kênh hiển thị,
 * tuyệt đối không được ghi có ví qua lượt truy cập trình duyệt.
 */
@Service
@RequiredArgsConstructor
public class VnPayWalletTopUpService {
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("50000000");
    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VNP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WalletTopUpRepository topUps;
    private final UserWalletRepository wallets;
    private final WalletTransactionRepository transactions;

    @Value("${skillshare.vnpay.tmn-code:}") private String tmnCode;
    @Value("${skillshare.vnpay.hash-secret:}") private String hashSecret;
    @Value("${skillshare.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") private String payUrl;
    @Value("${skillshare.vnpay.return-url:http://localhost:8080/customer/wallet/vnpay/return}") private String returnUrl;

    public boolean configured() {
        return tmnCode != null && !tmnCode.isBlank()
                && hashSecret != null && !hashSecret.isBlank()
                && returnUrl != null && !returnUrl.isBlank();
    }

    @Transactional(readOnly = true)
    public List<WalletTopUp> recentTopUps(User user) {
        return topUps.findTop10ByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public String createPayment(User user, BigDecimal amount, String ipAddress) {
        if (user == null || user.getRole() != Role.CUSTOMER)
            throw new IllegalArgumentException("Chỉ Customer mới được nạp ví.");
        if (!configured())
            throw new IllegalStateException("Chưa cấu hình VNPay Sandbox. Hãy thiết lập VNPAY_TMN_CODE, VNPAY_HASH_SECRET và VNPAY_RETURN_URL.");
        // VNPay xử lý đơn vị VND nguyên; không cho phép số tiền có phần lẻ.
        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0
                || amount.stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException("Số tiền nạp phải là số nguyên từ 10.000đ đến 50.000.000đ.");

        // Khởi tạo ví trước khi gửi người dùng sang cổng thanh toán.
        wallets.findByUser(user).orElseGet(() -> wallets.save(UserWallet.builder()
                .user(user).balance(BigDecimal.ZERO).lockedAmount(BigDecimal.ZERO).build()));

        String ref = "WS" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
        topUps.save(WalletTopUp.builder().user(user).txnRef(ref).amount(amount)
                .status("PENDING").build());

        LocalDateTime now = LocalDateTime.now(VNP_ZONE);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigIntegerExact().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", ref);
        params.put("vnp_OrderInfo", "Nap vi SkillShare " + ref);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", normalizeIp(ipAddress));
        params.put("vnp_CreateDate", now.format(VNP_DATE));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(VNP_DATE));

        String query = encodeParams(params);
        return payUrl + "?" + query + "&vnp_SecureHash=" + hmacSha512(hashSecret, query);
    }

    /** JSON response codes required by VNPay's server-to-server IPN integration. */
    public record IpnResult(String code, String message) {
        public Map<String, String> asMap() {
            return Map.of("RspCode", code, "Message", message);
        }
    }

    /**
     * IMPORTANT: Invoked ONLY from GET /customer/wallet/vnpay/ipn, not browser Return URL.
     * Pessimistic lock + single DB transaction make duplicate notifications idempotent.
     */
    @Transactional
    public IpnResult processIpn(Map<String, String> params) {
        if (!validSignature(params)) return new IpnResult("97", "Invalid signature");
        if (!validMerchant(params)) return new IpnResult("99", "Invalid merchant");

        String ref = params.get("vnp_TxnRef");
        if (ref == null || ref.isBlank()) return new IpnResult("01", "Order not found");
        WalletTopUp topUp = topUps.findByTxnRefForUpdate(ref).orElse(null);
        if (topUp == null) return new IpnResult("01", "Order not found");
        if (!validAmount(params, topUp.getAmount())) return new IpnResult("04", "Invalid amount");
        if (!"PENDING".equals(topUp.getStatus())) return new IpnResult("02", "Order already confirmed");

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        if (!twoDigitStatus(responseCode) || !twoDigitStatus(transactionStatus))
            return new IpnResult("99", "Invalid transaction status");

        if (!"00".equals(responseCode) || !"00".equals(transactionStatus)) {
            topUp.setStatus("FAILED");
            topUps.save(topUp);
            return new IpnResult("00", "Confirm Success");
        }

        String gatewayNo = params.get("vnp_TransactionNo");
        if (gatewayNo == null || !gatewayNo.matches("[0-9]{1,15}"))
            return new IpnResult("99", "Invalid transaction number");
        // Database unique index is the last line of defense against concurrent
        // signed notifications trying to reuse one gateway transaction for two references.
        if (topUps.existsByGatewayTransactionNo(gatewayNo))
            return new IpnResult("99", "Duplicate gateway transaction");

        UserWallet wallet = wallets.findByUserForUpdate(topUp.getUser())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy ví Customer."));
        if (wallet.getBalance().signum() < 0 || wallet.getLockedAmount().signum() < 0)
            throw new IllegalStateException("Số dư ví không hợp lệ; yêu cầu đối soát.");

        wallet.setBalance(wallet.getBalance().add(topUp.getAmount()));
        wallets.save(wallet);
        transactions.save(WalletTransaction.builder()
                .wallet(wallet).type("VNPAY_TOPUP").amount(topUp.getAmount())
                .balanceAfter(wallet.getBalance()).lockedAfter(wallet.getLockedAmount())
                .description("Nạp ví VNPay Sandbox, mã yêu cầu " + ref + ", mã VNPay " + gatewayNo)
                .build());
        topUp.setStatus("PAID");
        topUp.setGatewayTransactionNo(gatewayNo);
        topUp.setPaidAt(LocalDateTime.now());
        topUps.save(topUp);
        return new IpnResult("00", "Confirm Success");
    }

    /** Browser Return may display stored status, but NEVER changes wallet or top-up state. */
    @Transactional(readOnly = true)
    public String statusForReturn(Map<String, String> params, User user) {
        if (user == null || !validSignature(params) || !validMerchant(params)) return "INVALID";
        String ref = params.get("vnp_TxnRef");
        if (ref == null || ref.isBlank()) return "INVALID";
        return topUps.findByTxnRef(ref)
                .filter(t -> t.getUser().getId().equals(user.getId()) && validAmount(params, t.getAmount()))
                .map(WalletTopUp::getStatus)
                .orElse("INVALID");
    }

    private boolean validSignature(Map<String, String> params) {
        if (!configured() || params == null) return false;
        String received = params.get("vnp_SecureHash");
        if (received == null || !received.matches("(?i)[0-9a-f]{128}")) return false;
        String expected = hmacSha512(hashSecret, encodeParams(withoutHashes(params)));
        return MessageDigest.isEqual(received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
    }

    private boolean validMerchant(Map<String, String> params) {
        return Objects.equals(tmnCode, params.get("vnp_TmnCode"))
                && (params.get("vnp_CurrCode") == null || "VND".equals(params.get("vnp_CurrCode")));
    }

    private boolean validAmount(Map<String, String> params, BigDecimal expected) {
        String value = params.get("vnp_Amount");
        if (value == null || !value.matches("[0-9]{1,15}")) return false;
        try {
            return new BigDecimal(value).compareTo(expected.multiply(BigDecimal.valueOf(100))) == 0;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    private boolean twoDigitStatus(String status) {
        return status != null && status.matches("[0-9]{2}");
    }

    private Map<String, String> withoutHashes(Map<String, String> params) {
        Map<String, String> result = new TreeMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash")
                    && !key.equals("vnp_SecureHashType") && value != null && !value.isBlank())
                result.put(key, value);
        });
        return result;
    }

    private String encodeParams(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> joiner.add(URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return joiner.toString();
    }

    private String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Không thể xác minh chữ ký VNPay.", e);
        }
    }

    private String normalizeIp(String ip) {
        if (ip == null || ip.isBlank() || "0:0:0:0:0:0:0:1".equals(ip)) return "127.0.0.1";
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
