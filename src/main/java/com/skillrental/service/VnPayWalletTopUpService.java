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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class VnPayWalletTopUpService {
    private final WalletTopUpRepository topUps;
    private final UserWalletRepository wallets;
    private final WalletTransactionRepository transactions;
    @Value("${skillshare.vnpay.tmn-code:}") private String tmnCode;
    @Value("${skillshare.vnpay.hash-secret:}") private String hashSecret;
    @Value("${skillshare.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") private String payUrl;
    @Value("${skillshare.vnpay.return-url:http://localhost:8080/customer/wallet/vnpay/return}") private String returnUrl;

    public boolean configured() { return tmnCode != null && !tmnCode.isBlank() && hashSecret != null && !hashSecret.isBlank(); }

    @Transactional
    public String createPayment(User user, BigDecimal amount, String ipAddress) {
        if (user.getRole() != Role.CUSTOMER) throw new IllegalArgumentException("Chỉ Customer mới được nạp ví.");
        if (!configured()) throw new IllegalStateException("Chưa cấu hình VNPay Sandbox. Hãy thiết lập VNPAY_TMN_CODE và VNPAY_HASH_SECRET.");
        if (amount == null || amount.compareTo(new BigDecimal("10000")) < 0 || amount.compareTo(new BigDecimal("50000000")) > 0 || amount.scale() > 2)
            throw new IllegalArgumentException("Số tiền nạp phải từ 10.000đ đến 50.000.000đ.");
        String ref = "WS" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
        topUps.save(WalletTopUp.builder().user(user).txnRef(ref).amount(amount).status("PENDING").build());
        LocalDateTime now = LocalDateTime.now();
        Map<String,String> p = new TreeMap<>();
        p.put("vnp_Version", "2.1.0"); p.put("vnp_Command", "pay"); p.put("vnp_TmnCode", tmnCode);
        p.put("vnp_Amount", amount.multiply(new BigDecimal("100")).longValueExact() + "");
        p.put("vnp_CurrCode", "VND"); p.put("vnp_TxnRef", ref); p.put("vnp_OrderInfo", "Nap vi SkillShare " + ref);
        p.put("vnp_OrderType", "other"); p.put("vnp_Locale", "vn"); p.put("vnp_ReturnUrl", returnUrl);
        p.put("vnp_IpAddr", normalizeIp(ipAddress));
        p.put("vnp_CreateDate", now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        p.put("vnp_ExpireDate", now.plusMinutes(15).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        String query = encodeParams(p);
        String secureHash = hmacSha512(hashSecret, query);
        return payUrl + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    @Transactional
    public boolean processReturn(Map<String,String> params) {
        String provided = params.get("vnp_SecureHash");
        if (provided == null || !constantTimeEquals(provided.toLowerCase(Locale.ROOT), hmacSha512(hashSecret, encodeParams(withoutHashes(params))))) return false;
        String ref = params.get("vnp_TxnRef");
        WalletTopUp topup = topUps.findByTxnRefForUpdate(ref).orElse(null);
        if (topup == null) return false;
        if ("PAID".equals(topup.getStatus())) return "00".equals(params.get("vnp_ResponseCode"));
        if (!"00".equals(params.get("vnp_ResponseCode")) || !"00".equals(params.get("vnp_TransactionStatus"))) {
            topup.setStatus("FAILED"); topUps.save(topup); return false;
        }
        long returnedAmount;
        try { returnedAmount = Long.parseLong(params.getOrDefault("vnp_Amount", "0")); }
        catch (NumberFormatException ex) { return false; }
        if (returnedAmount != topup.getAmount().multiply(new BigDecimal("100")).longValueExact()) return false;
        UserWallet wallet = wallets.findByUserForUpdate(topup.getUser()).orElseThrow(() -> new IllegalStateException("Không tìm thấy ví Customer."));
        wallet.setBalance(wallet.getBalance().add(topup.getAmount())); wallets.save(wallet);
        transactions.save(WalletTransaction.builder().wallet(wallet).type("VNPAY_TOPUP").amount(topup.getAmount())
                .balanceAfter(wallet.getBalance()).lockedAfter(wallet.getLockedAmount())
                .description("Nạp ví thành công qua VNPay Sandbox, mã giao dịch " + ref).build());
        topup.setStatus("PAID"); topup.setGatewayTransactionNo(params.get("vnp_TransactionNo")); topup.setPaidAt(LocalDateTime.now()); topUps.save(topup);
        return true;
    }

    private Map<String,String> withoutHashes(Map<String,String> params) {
        Map<String,String> p = new TreeMap<>();
        params.forEach((k,v) -> { if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType") && v != null && !v.isBlank()) p.put(k,v); });
        return p;
    }
    private String encodeParams(Map<String,String> p) {
        StringJoiner j = new StringJoiner("&");
        p.forEach((k,v) -> j.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return j.toString();
    }
    private String hmacSha512(String key, String data) {
        try { Mac mac = Mac.getInstance("HmacSHA512"); mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Không thể ký yêu cầu VNPay.", e); }
    }
    private boolean constantTimeEquals(String a, String b) { return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }
    private String normalizeIp(String ip) { if (ip == null || ip.isBlank() || ip.equals("0:0:0:0:0:0:0:1")) return "127.0.0.1"; return ip.length() > 45 ? ip.substring(0,45) : ip; }
}
