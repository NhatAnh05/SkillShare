package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EscrowService {
    private static final Logger log = LoggerFactory.getLogger(EscrowService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private final RentalOrderRepository orders;
    private final BookingRepository bookings;
    private final UserWalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final ProviderWithdrawalRepository providerWithdrawals;
    private final CommissionPolicy commissionPolicy;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    @Value("${skillshare.escrow.show-demo-otp:true}") private boolean showDemoOtp;
    @Value("${skillshare.wallet.demo-topup-enabled:true}") private boolean demoTopupEnabled;

    public boolean isDemoTopupEnabled() { return demoTopupEnabled; }

    @Transactional
    public UserWallet wallet(User user) {
        return wallets.findByUser(user).orElseGet(() -> wallets.save(UserWallet.builder()
                .user(user).balance(ZERO).lockedAmount(ZERO).build()));
    }

    public List<WalletTransaction> transactions(User user) {
        return transactions.findByWalletOrderByCreatedAtDesc(wallet(user));
    }

    @Transactional
    public void topUpDemo(User user, BigDecimal amount) {
        if (!demoTopupEnabled) throw new IllegalStateException("Nạp ví demo đã bị tắt trong môi trường này.");
        if (amount == null || amount.compareTo(new BigDecimal("10000")) < 0 || amount.compareTo(new BigDecimal("50000000")) > 0) {
            throw new IllegalArgumentException("Số tiền nạp demo phải từ 10.000đ đến 50.000.000đ.");
        }
        UserWallet wallet = lockWallet(user);
        wallet.setBalance(wallet.getBalance().add(amount));
        wallets.save(wallet);
        record(wallet, null, "DEMO_TOPUP", amount, "Nạp thử nghiệm nội bộ; không phải giao dịch ngân hàng thật.");
    }

    @Transactional
    public WithdrawalOtpDispatchResult createWithdrawal(User provider, BigDecimal amount, String bankName,
                                                         String accountNumber, String accountHolder) {
        if (provider.getRole() != Role.PROVIDER) throw new IllegalArgumentException("Chỉ Provider mới được yêu cầu rút tiền.");
        if (amount == null || amount.compareTo(new BigDecimal("2000000")) < 0) {
            throw new IllegalArgumentException("Số tiền rút tối thiểu là 2.000.000đ.");
        }
        if (bankName == null || bankName.isBlank() || accountNumber == null || accountNumber.isBlank()
                || accountHolder == null || accountHolder.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập ngân hàng, số tài khoản và tên chủ tài khoản.");
        }
        UserWallet wallet = lockWallet(provider);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Số dư khả dụng không đủ để tạo yêu cầu rút tiền.");
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallets.save(wallet);
        record(wallet, null, "WITHDRAWAL_PENDING", amount.negate(), "Tạm giữ tiền cho yêu cầu rút tiền đang chờ OTP.");

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        ProviderWithdrawal withdrawal = providerWithdrawals.save(ProviderWithdrawal.builder()
                .provider(provider).amount(safe(amount)).bankName(bankName.trim())
                .accountNumber(accountNumber.trim()).accountHolder(accountHolder.trim())
                .status("PENDING_OTP").otpHash(passwordEncoder.encode(code))
                .otpExpiresAt(LocalDateTime.now().plusMinutes(10)).otpAttempts(0).build());
        boolean sent = sendWithdrawalOtp(withdrawal, code);
        String demoCode = !sent && showDemoOtp ? code : null;
        if (!sent && !showDemoOtp) {
            // Do not leave Provider funds stranded if neither email OTP nor demo OTP is available.
            wallet.setBalance(wallet.getBalance().add(amount));
            wallets.save(wallet);
            record(wallet, null, "WITHDRAWAL_RETURN", amount, "Hoàn tiền vì không thể phát hành OTP rút tiền WD" + withdrawal.getId());
            withdrawal.setStatus("REJECTED");
            withdrawal.setOtpHash(null);
            withdrawal.setOtpExpiresAt(null);
            withdrawal.setAdminNote("Không gửi được OTP; tiền đã tự động hoàn lại vào ví.");
            providerWithdrawals.save(withdrawal);
        }
        return new WithdrawalOtpDispatchResult(withdrawal.getId(), sent, demoCode);
    }

    @Transactional
    public WithdrawalOtpDispatchResult resendWithdrawalOtp(Long withdrawalId, User provider) {
        ProviderWithdrawal withdrawal = providerWithdrawals.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));
        if (!withdrawal.getProvider().getId().equals(provider.getId())) throw new IllegalArgumentException("Bạn không có quyền với yêu cầu này.");
        if (!"PENDING_OTP".equals(withdrawal.getStatus())) throw new IllegalStateException("Yêu cầu không còn chờ OTP.");
        if (withdrawal.getOtpAttempts() >= 5) throw new IllegalStateException("Đã nhập sai OTP quá 5 lần. Hãy liên hệ Admin.");
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        withdrawal.setOtpHash(passwordEncoder.encode(code));
        withdrawal.setOtpExpiresAt(LocalDateTime.now().plusMinutes(10));
        // Keep failed-attempt count; requesting another code must not reset the security limit.
        providerWithdrawals.save(withdrawal);
        boolean sent = sendWithdrawalOtp(withdrawal, code);
        if (!sent && !showDemoOtp) {
            UserWallet wallet = lockWallet(withdrawal.getProvider());
            wallet.setBalance(wallet.getBalance().add(withdrawal.getAmount()));
            wallets.save(wallet);
            record(wallet, null, "WITHDRAWAL_RETURN", withdrawal.getAmount(), "Hoàn tiền vì không thể gửi lại OTP cho WD" + withdrawal.getId());
            withdrawal.setStatus("REJECTED");
            withdrawal.setOtpHash(null);
            withdrawal.setOtpExpiresAt(null);
            withdrawal.setAdminNote("Không gửi được OTP; tiền đã tự động hoàn lại vào ví.");
            providerWithdrawals.save(withdrawal);
        }
        return new WithdrawalOtpDispatchResult(withdrawal.getId(), sent, !sent && showDemoOtp ? code : null);
    }

    @Transactional
    public void cancelUnverifiedWithdrawal(Long withdrawalId, User provider) {
        ProviderWithdrawal withdrawal = providerWithdrawals.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));
        if (!withdrawal.getProvider().getId().equals(provider.getId())) throw new IllegalArgumentException("Bạn không có quyền với yêu cầu này.");
        if (!"PENDING_OTP".equals(withdrawal.getStatus())) throw new IllegalStateException("Chỉ có thể hủy yêu cầu đang chờ OTP.");
        UserWallet wallet = lockWallet(withdrawal.getProvider());
        wallet.setBalance(wallet.getBalance().add(withdrawal.getAmount()));
        wallets.save(wallet);
        record(wallet, null, "WITHDRAWAL_RETURN", withdrawal.getAmount(), "Provider hủy yêu cầu rút tiền chưa xác minh WD" + withdrawal.getId() + "; hoàn tiền vào ví.");
        withdrawal.setStatus("REJECTED");
        withdrawal.setOtpHash(null);
        withdrawal.setOtpExpiresAt(null);
        withdrawal.setAdminNote("Provider chủ động hủy yêu cầu trước khi xác minh OTP; tiền đã hoàn về ví.");
        providerWithdrawals.save(withdrawal);
    }

    private boolean sendWithdrawalOtp(ProviderWithdrawal withdrawal, String code) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            if (showDemoOtp) log.warn("DEMO OTP rút tiền WD{} / {}: {} (chưa cấu hình SMTP)",
                    withdrawal.getId(), withdrawal.getProvider().getEmail(), code);
            return false;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(withdrawal.getProvider().getEmail());
            mail.setSubject("OTP xác nhận yêu cầu rút tiền SkillShare #WD" + withdrawal.getId());
            mail.setText("Mã OTP xác nhận yêu cầu rút " + withdrawal.getAmount().toPlainString()
                    + "đ tới " + withdrawal.getBankName() + " / tài khoản " + withdrawal.getAccountNumber()
                    + " là: " + code + "\nMã có hiệu lực trong 10 phút. Đây là quy trình demo nội bộ, chưa chuyển khoản ngân hàng thật.");
            sender.send(mail);
            return true;
        } catch (Exception e) {
            log.warn("Không gửi được OTP rút tiền WD{}: {}", withdrawal.getId(), e.getMessage());
            return false;
        }
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyWithdrawalOtp(Long withdrawalId, User provider, String otp) {
        ProviderWithdrawal withdrawal = providerWithdrawals.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));
        if (!withdrawal.getProvider().getId().equals(provider.getId())) throw new IllegalArgumentException("Bạn không có quyền với yêu cầu này.");
        if (!"PENDING_OTP".equals(withdrawal.getStatus())) throw new IllegalStateException("Yêu cầu rút tiền không ở trạng thái chờ OTP.");
        if (withdrawal.getOtpAttempts() >= 5) throw new IllegalStateException("Đã nhập sai OTP quá 5 lần. Hãy liên hệ Admin.");
        if (withdrawal.getOtpExpiresAt() == null || withdrawal.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("OTP đã hết hạn. Hãy tạo yêu cầu mới hoặc liên hệ Admin.");
        }
        if (otp == null || !passwordEncoder.matches(otp.trim(), withdrawal.getOtpHash())) {
            withdrawal.setOtpAttempts(withdrawal.getOtpAttempts() + 1);
            providerWithdrawals.save(withdrawal);
            throw new IllegalArgumentException("OTP rút tiền không đúng.");
        }
        withdrawal.setStatus("PENDING_ADMIN");
        withdrawal.setVerifiedAt(LocalDateTime.now());
        withdrawal.setOtpHash(null);
        withdrawal.setOtpExpiresAt(null);
        providerWithdrawals.save(withdrawal);
    }

    @Transactional
    public void approveWithdrawal(Long withdrawalId, User admin) {
        if (admin.getRole() != Role.ADMIN) throw new IllegalArgumentException("Chỉ Admin mới được duyệt rút tiền.");
        ProviderWithdrawal withdrawal = providerWithdrawals.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));
        if (!"PENDING_ADMIN".equals(withdrawal.getStatus())) throw new IllegalStateException("Yêu cầu phải xác minh OTP trước khi duyệt.");
        withdrawal.setStatus("APPROVED_DEMO");
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawal.setProcessedByAdmin(admin);
        withdrawal.setAdminNote("Đã duyệt trong demo; chưa phát sinh giao dịch chuyển khoản ngân hàng thật.");
        providerWithdrawals.save(withdrawal);
    }

    @Transactional
    public void rejectWithdrawal(Long withdrawalId, User admin, String reason) {
        if (admin.getRole() != Role.ADMIN) throw new IllegalArgumentException("Chỉ Admin mới được từ chối yêu cầu rút tiền.");
        ProviderWithdrawal withdrawal = providerWithdrawals.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));
        if (!"PENDING_ADMIN".equals(withdrawal.getStatus())) throw new IllegalStateException("Yêu cầu phải xác minh OTP trước khi xử lý.");
        UserWallet wallet = lockWallet(withdrawal.getProvider());
        wallet.setBalance(wallet.getBalance().add(withdrawal.getAmount()));
        wallets.save(wallet);
        record(wallet, null, "WITHDRAWAL_RETURN", withdrawal.getAmount(), "Admin từ chối rút tiền WD" + withdrawal.getId() + "; trả lại tiền vào ví. ");
        withdrawal.setStatus("REJECTED");
        withdrawal.setProcessedAt(LocalDateTime.now());
        withdrawal.setProcessedByAdmin(admin);
        withdrawal.setAdminNote(reason == null || reason.isBlank() ? "Admin từ chối yêu cầu rút tiền." : reason.trim());
        providerWithdrawals.save(withdrawal);
    }

    @Transactional
    public void reserveForBooking(RentalOrder order) {
        UserWallet wallet = lockWallet(order.getCustomer());
        BigDecimal amount = safe(order.getTotal());
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Số dư khả dụng không đủ. Cần " + amount.toPlainString()
                    + "đ, hiện có " + wallet.getBalance().toPlainString() + "đ. Vui lòng nạp ví trước khi đặt.");
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setLockedAmount(wallet.getLockedAmount().add(amount));
        wallets.save(wallet);
        order.setLockedAmount(amount);
        orders.save(order);
        record(wallet, order, "HOLD", amount, "Khóa tiền bảo đảm cho đơn DH" + order.getId());
    }

    @Transactional
    public void providerAccept(Long orderId, User provider) {
        RentalOrder order = providerOrder(orderId, provider);
        requireStatus(order, OrderStatus.PENDING_PROVIDER_ACCEPTANCE);
        order.setProviderAccepted(true);
        order.setStatus(OrderStatus.PENDING_START_CONFIRMATION);
        if (order.getBooking() != null) {
            order.getBooking().setStatus(BookingStatus.CONFIRMED);
            bookings.save(order.getBooking());
        }
        orders.save(order);
    }

    @Transactional
    public void providerDecline(Long orderId, User provider, String reason) {
        RentalOrder order = providerOrder(orderId, provider);
        requireStatus(order, OrderStatus.PENDING_PROVIDER_ACCEPTANCE);
        String why = (reason == null || reason.isBlank())
                ? "Provider từ chối yêu cầu trước khi nhận việc; hoàn lại toàn bộ khoản ký quỹ cho Customer."
                : reason.trim();
        cancelAndRelease(order, provider, why);
    }

    @Transactional
    public void confirmStart(Long orderId, User actor, Role role) {
        RentalOrder order = role == Role.PROVIDER ? providerOrder(orderId, actor) : customerOrder(orderId, actor);
        requireStatus(order, OrderStatus.PENDING_START_CONFIRMATION);
        if (role == Role.PROVIDER) order.setProviderStarted(true);
        else if (role == Role.CUSTOMER) order.setCustomerStarted(true);
        else throw new IllegalArgumentException("Vai trò không được xác nhận bắt đầu công việc.");
        if (Boolean.TRUE.equals(order.getProviderStarted()) && Boolean.TRUE.equals(order.getCustomerStarted())) {
            order.setStatus(OrderStatus.PROCESSING);
            order.setStartDepositAmount(safe(order.getTotal()).divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP));
            if (order.getBooking() != null) {
                order.getBooking().setStatus(BookingStatus.IN_PROGRESS);
                bookings.save(order.getBooking());
            }
        }
        orders.save(order);
    }

    @Transactional
    public void providerComplete(Long orderId, User provider) {
        RentalOrder order = providerOrder(orderId, provider);
        requireStatus(order, OrderStatus.PROCESSING);
        if (!Boolean.TRUE.equals(order.getCustomerStarted()) || !Boolean.TRUE.equals(order.getProviderStarted())) {
            throw new IllegalStateException("Hai bên phải xác nhận bắt đầu trước khi báo hoàn thành.");
        }
        order.setProviderCompleted(true);
        order.setStatus(OrderStatus.PENDING_CUSTOMER_ACCEPTANCE);
        orders.save(order);
    }

    @Transactional
    public OtpDispatchResult customerAcceptCompletion(Long orderId, User customer) {
        RentalOrder order = customerOrder(orderId, customer);
        requireStatus(order, OrderStatus.PENDING_CUSTOMER_ACCEPTANCE);
        order.setCustomerAcceptedCompletion(true);
        return issueCompletionOtp(order, customer, true);
    }

    @Transactional
    public OtpDispatchResult resendCompletionOtp(Long orderId, User customer) {
        RentalOrder order = customerOrder(orderId, customer);
        requireStatus(order, OrderStatus.PENDING_PAYMENT_OTP);
        if (order.getCompletionOtpAttempts() != null && order.getCompletionOtpAttempts() >= 5) {
            throw new IllegalStateException("Đã nhập sai OTP quá 5 lần. Hãy liên hệ hỗ trợ để xác minh lại.");
        }
        return issueCompletionOtp(order, customer, false);
    }

    private OtpDispatchResult issueCompletionOtp(RentalOrder order, User customer, boolean firstIssue) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        order.setCompletionOtpHash(passwordEncoder.encode(code));
        order.setCompletionOtpExpiresAt(LocalDateTime.now().plusMinutes(10));
        if (firstIssue) order.setCompletionOtpAttempts(0);
        order.setStatus(OrderStatus.PENDING_PAYMENT_OTP);
        orders.save(order);

        boolean sent = false;
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender != null) {
            try {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setTo(customer.getEmail());
                mail.setSubject("Mã OTP xác nhận quyết toán SkillShare - DH" + order.getId());
                mail.setText("Mã OTP xác nhận hoàn thành và giải ngân đơn DH" + order.getId() + " là: " + code
                        + "\nMã có hiệu lực trong 10 phút. Nếu bạn không thực hiện thao tác này, hãy bỏ qua email.");
                sender.send(mail);
                sent = true;
            } catch (Exception e) {
                log.warn("Không gửi được email OTP cho DH{}: {}", order.getId(), e.getMessage());
            }
        }
        if (!sent && showDemoOtp) log.warn("DEMO OTP cho DH{} / {}: {} (chưa cấu hình SMTP gửi email thật)", order.getId(), customer.getEmail(), code);
        else if (!sent) log.error("Không thể gửi OTP quyết toán DH{} vì SMTP chưa được cấu hình hoặc gửi thất bại.", order.getId());
        return new OtpDispatchResult(sent, sent || !showDemoOtp ? null : code);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyOtpAndSettle(Long orderId, User customer, String otp) {
        RentalOrder order = customerOrder(orderId, customer);
        requireStatus(order, OrderStatus.PENDING_PAYMENT_OTP);
        if (order.getCompletionOtpAttempts() != null && order.getCompletionOtpAttempts() >= 5) {
            throw new IllegalStateException("Đã nhập sai OTP quá 5 lần. Hãy liên hệ hỗ trợ để xác minh lại.");
        }
        if (order.getCompletionOtpExpiresAt() == null || order.getCompletionOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("OTP đã hết hạn. Hãy dùng nút “Gửi lại mã OTP” để nhận mã mới.");
        }
        if (otp == null || !passwordEncoder.matches(otp.trim(), order.getCompletionOtpHash())) {
            order.setCompletionOtpAttempts((order.getCompletionOtpAttempts() == null ? 0 : order.getCompletionOtpAttempts()) + 1);
            orders.save(order);
            throw new IllegalArgumentException("Mã OTP không đúng.");
        }
        settle(order, safe(order.getTotal()), true);
    }

    @Transactional
    public void cancelByCustomer(Long orderId, User customer) {
        RentalOrder order = customerOrder(orderId, customer);
        if (isTerminal(order)) throw new IllegalStateException("Đơn đã kết thúc, không thể hủy.");
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT_OTP) {
            throw new IllegalStateException("Đơn đang chờ xác nhận OTP quyết toán; không thể hủy trực tiếp.");
        }
        if (order.getStatus() == OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER
                || order.getStatus() == OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER) {
            throw new IllegalStateException("Yêu cầu hủy đã được ghi nhận. Hãy chờ hoặc xử lý đề xuất chi phí từ Provider.");
        }
        if (order.getStatus() == OrderStatus.PROCESSING || order.getStatus() == OrderStatus.PENDING_CUSTOMER_ACCEPTANCE) {
            order.setStatus(OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER);
            order.setCancellationStartedAt(LocalDateTime.now());
            order.setCancellationReason("Khách hàng yêu cầu hủy sau khi hai bên xác nhận bắt đầu; chờ Provider đề xuất chi phí phần việc đã thực hiện.");
            orders.save(order);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING_PROVIDER_ACCEPTANCE
                && order.getStatus() != OrderStatus.PENDING_START_CONFIRMATION
                && order.getStatus() != OrderStatus.PENDING_PAYMENT
                && order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Trạng thái hiện tại không cho phép Customer hủy đơn theo luồng này.");
        }
        cancelAndRelease(order, customer, "Khách hàng hủy trước khi hai bên xác nhận bắt đầu; hoàn lại toàn bộ khoản ký quỹ.");
    }

    @Transactional
    public void cancelByProvider(Long orderId, User provider, String reason) {
        RentalOrder order = providerOrder(orderId, provider);
        if (isTerminal(order) || order.getStatus() == OrderStatus.PENDING_PAYMENT_OTP) {
            throw new IllegalStateException("Đơn đã kết thúc hoặc đang quyết toán, không thể hủy ở bước này.");
        }
        String why = (reason == null || reason.isBlank()) ? "Provider hủy công việc; hoàn 100% tiền ký quỹ cho khách." : reason.trim();
        order.setCancellationReason(why);
        cancelAndRelease(order, provider, "Provider hủy đơn: hoàn 100% khoản đang ký quỹ cho khách. " + why);
    }

    @Transactional
    public void proposeCancellationCompensation(Long orderId, User provider, BigDecimal amount, String note) {
        RentalOrder order = providerOrder(orderId, provider);
        requireStatus(order, OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER);
        BigDecimal proposed = safe(amount);
        if (proposed.signum() < 0 || proposed.compareTo(safe(order.getLockedAmount())) > 0) {
            throw new IllegalArgumentException("Chi phí đề xuất phải nằm trong khoảng 0 đến số tiền đang ký quỹ.");
        }
        order.setCancellationCompensation(proposed);
        order.setCancellationReason(note == null || note.isBlank() ? "Provider đề xuất chi phí phần việc đã thực hiện." : note.trim());
        order.setStatus(OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER);
        orders.save(order);
    }

    @Transactional
    public void customerResolveCompensation(Long orderId, User customer, boolean accept) {
        RentalOrder order = customerOrder(orderId, customer);
        requireStatus(order, OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER);
        if (accept) {
            settle(order, safe(order.getCancellationCompensation()), false);
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledBy(customer);
            order.setCancelledAt(LocalDateTime.now());
            if (order.getBooking() != null) {
                order.getBooking().setStatus(BookingStatus.CANCELLED);
                order.getBooking().setCancelledBy(customer);
                order.getBooking().setCancelledAt(LocalDateTime.now());
                order.getBooking().setCancellationReason("Hai bên thống nhất chi phí phần việc đã thực hiện.");
                bookings.save(order.getBooking());
            }
            orders.save(order);
        } else {
            order.setCancellationCompensation(null);
            order.setStatus(OrderStatus.PROCESSING);
            order.setCancellationReason("Khách hàng chưa chấp nhận chi phí hủy; công việc trở lại trạng thái đang thực hiện.");
            orders.save(order);
        }
    }

    private void settle(RentalOrder order, BigDecimal providerAmount, boolean completed) {
        BigDecimal held = safe(order.getLockedAmount());
        BigDecimal payout = safe(providerAmount);
        if (payout.compareTo(held) > 0) throw new IllegalStateException("Số tiền chi trả vượt khoản ký quỹ.");
        UserWallet customerWallet = lockWallet(order.getCustomer());
        UserWallet providerWallet = lockWallet(order.getProvider());
        if (customerWallet.getLockedAmount().compareTo(held) < 0) {
            throw new IllegalStateException("Số dư ký quỹ không khớp với đơn hàng; đã chặn quyết toán để bảo vệ tiền.");
        }
        customerWallet.setLockedAmount(customerWallet.getLockedAmount().subtract(held));
        BigDecimal returned = held.subtract(payout);
        BigDecimal platformFee = commissionPolicy.fee(payout);
        BigDecimal providerNet = payout.subtract(platformFee).setScale(2, RoundingMode.HALF_UP);
        customerWallet.setBalance(customerWallet.getBalance().add(returned));
        providerWallet.setBalance(providerWallet.getBalance().add(providerNet));
        wallets.save(customerWallet);
        wallets.save(providerWallet);
        record(customerWallet, order, completed ? "SETTLEMENT_OUT" : "CANCELLATION_REFUND", payout.negate(),
                completed ? "Quyết toán tổng DH" + order.getId() + " sau OTP Customer." : "Chi trả tổng phần việc theo thỏa thuận hủy DH" + order.getId());
        if (returned.signum() > 0) record(customerWallet, order, "UNHOLD_REFUND", returned, "Hoàn phần ký quỹ còn lại của đơn DH" + order.getId());
        if (providerNet.signum() > 0) record(providerWallet, order, "SETTLEMENT_IN", providerNet,
                "Provider thực nhận từ DH" + order.getId() + " sau phí sàn " + platformFee.toPlainString() + "đ (" + commissionPolicy.rate().multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "% cấu hình). Tổng tính phí: " + payout.toPlainString() + "đ.");
        order.setPlatformFeeAmount(platformFee);
        order.setProviderNetAmount(providerNet);
        order.setLockedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        order.setSettledAt(LocalDateTime.now());
        order.setCompletionOtpHash(null);
        order.setCompletionOtpExpiresAt(null);
        if (completed) {
            order.setStatus(OrderStatus.COMPLETED);
            order.setCompletedAt(LocalDateTime.now());
            if (order.getBooking() != null) {
                order.getBooking().setStatus(BookingStatus.COMPLETED);
                bookings.save(order.getBooking());
            }
        }
        orders.save(order);
    }

    private void cancelAndRelease(RentalOrder order, User actor, String reason) {
        BigDecimal held = safe(order.getLockedAmount());
        if (held.signum() > 0) {
            UserWallet customerWallet = lockWallet(order.getCustomer());
            if (customerWallet.getLockedAmount().compareTo(held) < 0) throw new IllegalStateException("Khoản ký quỹ không khớp; cần Admin đối soát trước khi hủy.");
            customerWallet.setLockedAmount(customerWallet.getLockedAmount().subtract(held));
            customerWallet.setBalance(customerWallet.getBalance().add(held));
            wallets.save(customerWallet);
            record(customerWallet, order, "UNHOLD_REFUND", held, reason);
            order.setLockedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(actor);
        if (order.getCancellationReason() == null || order.getCancellationReason().isBlank()) order.setCancellationReason(reason);
        if (order.getBooking() != null) {
            order.getBooking().setStatus(BookingStatus.CANCELLED);
            order.getBooking().setCancelledAt(LocalDateTime.now());
            order.getBooking().setCancelledBy(actor);
            order.getBooking().setCancellationReason(reason);
            bookings.save(order.getBooking());
        }
        orders.save(order);
    }

    private UserWallet lockWallet(User user) {
        return wallets.findByUserForUpdate(user).orElseGet(() -> wallet(user));
    }

    private void record(UserWallet wallet, RentalOrder order, String type, BigDecimal amount, String description) {
        transactions.save(WalletTransaction.builder().wallet(wallet).order(order).type(type).amount(amount)
                .balanceAfter(wallet.getBalance()).lockedAfter(wallet.getLockedAmount()).description(description).build());
    }
    private RentalOrder customerOrder(Long id, User customer) {
        RentalOrder o = orders.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (o.getCustomer() == null || !o.getCustomer().getId().equals(customer.getId())) throw new IllegalArgumentException("Bạn không có quyền thao tác đơn hàng này.");
        return o;
    }
    private RentalOrder providerOrder(Long id, User provider) {
        RentalOrder o = orders.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (o.getProvider() == null || !o.getProvider().getId().equals(provider.getId())) throw new IllegalArgumentException("Bạn không có quyền thao tác đơn hàng này.");
        return o;
    }
    private void requireStatus(RentalOrder order, OrderStatus expected) {
        if (order.getStatus() != expected) throw new IllegalStateException("Thao tác không hợp lệ với trạng thái đơn hiện tại: " + order.getStatus());
    }
    private boolean isTerminal(RentalOrder order) { return order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED; }
    private BigDecimal safe(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP); }

    public record OtpDispatchResult(boolean emailSent, String demoCode) {}
    public record WithdrawalOtpDispatchResult(Long withdrawalId, boolean emailSent, String demoCode) {}
}
