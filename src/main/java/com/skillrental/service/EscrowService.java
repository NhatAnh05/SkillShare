package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.notification.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private static final int COMPLETION_OTP_MAX_ATTEMPTS = 5;
    private static final int COMPLETION_OTP_MAX_RESENDS = 3;
    private static final int COMPLETION_OTP_RESEND_WAIT_SECONDS = 60;
    private final RentalOrderRepository orders;
    private final BookingRepository bookings;
    private final DeliverySubmissionRepository deliveries;
    private final UserWalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final EscrowSettlementRepository settlements;
    private final ProviderWithdrawalRepository providerWithdrawals;
    private final CommissionPolicy commissionPolicy;
    private final NotificationService notifications;
    private final RentalOrderStatusHistoryRepository orderHistory;
    private final ApplicationEventPublisher events;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    @Value("${skillshare.escrow.show-demo-otp:true}") private boolean showDemoOtp;
    @Value("${skillshare.wallet.demo-topup-enabled:true}") private boolean demoTopupEnabled;
    @Value("${spring.mail.username:}") private String smtpFromAddress;

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
        if (user == null || user.getRole() != Role.CUSTOMER)
            throw new IllegalArgumentException("Chỉ Customer mới được nạp ví.");
        if (!demoTopupEnabled) throw new IllegalStateException("Nạp ví demo đã bị tắt trong môi trường này.");
        if (amount == null || amount.compareTo(new BigDecimal("10000")) < 0
                || amount.compareTo(new BigDecimal("50000000")) > 0
                || amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Số tiền nạp demo phải là số nguyên từ 10.000đ đến 50.000.000đ.");
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

    /** Số tiền có thể dùng để đặt đơn, không bao gồm phần đang giữ ký quỹ. */
    @Transactional(readOnly = true)
    public BigDecimal availableBalance(User customer) {
        if (customer == null || customer.getRole() != Role.CUSTOMER) return ZERO;
        return wallets.findByUser(customer).map(UserWallet::getBalance).map(this::safe).orElse(ZERO);
    }

    /** Kiểm tra trước khi tạo booking; thao tác giữ tiền sẽ kiểm tra lại dưới khóa database. */
    @Transactional(readOnly = true)
    public void requireSufficientBalance(User customer, BigDecimal required) {
        BigDecimal amount = validHoldAmount(required);
        BigDecimal available = availableBalance(customer);
        if (available.compareTo(amount) < 0) {
            throw new InsufficientWalletBalanceException(amount, available);
        }
    }

    @Transactional
    public void reserveForBooking(RentalOrder order) {
        if (order == null || order.getCustomer() == null || order.getCustomer().getRole() != Role.CUSTOMER) {
            throw new IllegalArgumentException("Chỉ đơn của Customer mới được giữ tiền ký quỹ.");
        }
        if (order.getId() == null || order.getStatus() != OrderStatus.PENDING_PROVIDER_ACCEPTANCE) {
            throw new IllegalStateException("Trạng thái đơn không hợp lệ để giữ tiền.");
        }
        if (safe(order.getLockedAmount()).signum() != 0) {
            throw new IllegalStateException("Đơn này đã có khoản tiền ký quỹ; không được khóa hai lần.");
        }
        BigDecimal amount = validHoldAmount(order.getTotal());
        // Khóa dòng ví trước khi kiểm tra cuối cùng để hai booking đồng thời không thể chi vượt số dư.
        UserWallet wallet = wallets.findByUserForUpdate(order.getCustomer()).orElse(null);
        BigDecimal available = wallet == null ? ZERO : safe(wallet.getBalance());
        if (available.compareTo(amount) < 0) {
            throw new InsufficientWalletBalanceException(amount, available);
        }
        wallet.setBalance(available.subtract(amount));
        wallet.setLockedAmount(safe(wallet.getLockedAmount()).add(amount));
        wallets.save(wallet);
        order.setLockedAmount(amount);
        orders.save(order);
        record(wallet, order, "HOLD", amount, "Khóa tiền bảo đảm cho đơn DH" + order.getId()
                + "; chưa chuyển tiền cho Provider.");
    }

    private BigDecimal validHoldAmount(BigDecimal required) {
        if (required == null || required.signum() <= 0 || required.scale() > 2) {
            throw new IllegalArgumentException("Giá trị đơn phải lớn hơn 0 và có tối đa 2 chữ số thập phân.");
        }
        return safe(required);
    }

    public static final class InsufficientWalletBalanceException extends IllegalStateException {
        private final BigDecimal required;
        private final BigDecimal available;

        public InsufficientWalletBalanceException(BigDecimal required, BigDecimal available) {
            super("Số dư ví không đủ để đặt Provider. Cần " + required.toPlainString()
                    + "đ, hiện có " + available.toPlainString() + "đ, thiếu "
                    + required.subtract(available).toPlainString() + "đ. Vui lòng nạp tiền vào ví.");
            this.required = required;
            this.available = available;
        }

        public BigDecimal getRequired() { return required; }
        public BigDecimal getAvailable() { return available; }
        public BigDecimal getShortfall() { return required.subtract(available); }
    }

    @Transactional
    public void providerAccept(Long orderId, User provider) {
        RentalOrder order = providerOrderForUpdate(orderId, provider);
        requireStatus(order, OrderStatus.PENDING_PROVIDER_ACCEPTANCE);
        OrderStatus previousStatus = order.getStatus();
        order.setProviderAccepted(true);
        order.setStatus(OrderStatus.PENDING_START_CONFIRMATION);
        if (order.getBooking() != null) {
            order.getBooking().setStatus(BookingStatus.CONFIRMED);
            bookings.save(order.getBooking());
        }
        orders.save(order);
        recordStatusChange(order, previousStatus, provider, "Provider đã xác nhận nhận yêu cầu.");
    }

    @Transactional
    public void providerDecline(Long orderId, User provider, String reason) {
        RentalOrder order = providerOrderForUpdate(orderId, provider);
        requireStatus(order, OrderStatus.PENDING_PROVIDER_ACCEPTANCE);
        String why = (reason == null || reason.isBlank())
                ? "Provider từ chối yêu cầu trước khi nhận việc; hoàn lại toàn bộ khoản ký quỹ cho Customer."
                : reason.trim();
        refundAndCloseProviderCancellation(order, provider, why);
    }

    /**
     * Acknowledges one party's start confirmation under a pessimistic order lock.
     * Exactly once after BOTH parties confirm, earmarks one third of the already held total.
     * No wallet balance is changed and nothing is paid to the Provider in this method.
     */
    @Transactional
    public void confirmStart(Long orderId, User actor, Role role) {
        if (actor == null || actor.getId() == null || actor.getRole() != role
                || (role != Role.CUSTOMER && role != Role.PROVIDER)) {
            throw new IllegalArgumentException("Chỉ Customer hoặc Provider của đơn mới được xác nhận bắt đầu.");
        }
        RentalOrder order = role == Role.PROVIDER
                ? providerOrderForUpdate(orderId, actor) : customerOrderForUpdate(orderId, actor);
        requireStatus(order, OrderStatus.PENDING_START_CONFIRMATION);
        if (!Boolean.TRUE.equals(order.getProviderAccepted())) {
            throw new IllegalStateException("Provider phải nhận yêu cầu trước khi hai bên xác nhận bắt đầu.");
        }
        BigDecimal total = validHoldAmount(order.getTotal());
        if (safe(order.getLockedAmount()).compareTo(total) != 0) {
            throw new IllegalStateException("Số tiền ký quỹ của đơn không khớp giá trị hợp đồng; không thể bắt đầu.");
        }
        if (order.getStartDepositAmount() != null || order.getStartDepositState() != null
                || order.getStartDepositReservedAt() != null) {
            throw new IllegalStateException("Mốc tạm ứng đã được ghi nhận; không thể tạo lần thứ hai.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (role == Role.PROVIDER) {
            if (Boolean.TRUE.equals(order.getProviderStarted()))
                throw new IllegalStateException("Provider đã xác nhận bắt đầu trước đó.");
            order.setProviderStarted(true);
            order.setProviderStartConfirmedAt(now);
        } else {
            if (Boolean.TRUE.equals(order.getCustomerStarted()))
                throw new IllegalStateException("Customer đã xác nhận bắt đầu trước đó.");
            order.setCustomerStarted(true);
            order.setCustomerStartConfirmedAt(now);
        }

        OrderStatus previousStatus = order.getStatus();
        boolean bothConfirmed = Boolean.TRUE.equals(order.getProviderStarted())
                && Boolean.TRUE.equals(order.getCustomerStarted());
        if (bothConfirmed) {
            BigDecimal milestone = total.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
            order.setStartDepositAmount(milestone);
            order.setStartDepositState(StartDepositState.RESERVED);
            order.setStartDepositReservedAt(now);
            order.setWorkStartedAt(now);
            order.setStatus(OrderStatus.PROCESSING);
            if (order.getBooking() != null) {
                order.getBooking().setStatus(BookingStatus.IN_PROGRESS);
                bookings.save(order.getBooking());
            }
            orders.save(order);
            recordStatusChange(order, previousStatus, actor,
                    "Hai bên xác nhận bắt đầu. Ghi nhận mốc bảo đảm 1/3: "
                    + milestone.toPlainString() + "đ trong tổng ký quỹ " + total.toPlainString()
                    + "đ; chưa chuyển tiền cho Provider.");
        } else {
            orders.save(order);
            // Same-status history records the individual acknowledgement and its actor.
            recordStatusChange(order, previousStatus, actor,
                    (role == Role.CUSTOMER ? "Customer" : "Provider")
                    + " đã xác nhận bắt đầu; đang chờ bên còn lại. Không phát sinh thanh toán.");
        }
    }

    @Transactional
    public void providerComplete(Long orderId, User provider) {
        RentalOrder order = providerOrderForUpdate(orderId, provider);
        requireStatus(order, OrderStatus.PROCESSING);
        if (!Boolean.TRUE.equals(order.getCustomerStarted()) || !Boolean.TRUE.equals(order.getProviderStarted())) {
            throw new IllegalStateException("Hai bên phải xác nhận bắt đầu trước khi báo hoàn thành.");
        }
        DeliverySubmission latest = deliveries.findFirstByOrderIdOrderByVersionDesc(orderId)
                .orElseThrow(() -> new IllegalStateException("Phải nộp sản phẩm trước khi báo hoàn thành."));
        if (!"SUBMITTED".equals(latest.getStatus()) || (latest.getFiles().isEmpty() && latest.getLinks().isEmpty()))
            throw new IllegalStateException("Bản bàn giao cần có ít nhất một tệp hoặc liên kết sản phẩm hợp lệ.");
        OrderStatus previousStatus = order.getStatus();
        order.setProgressPercent(100);
        order.setDeliveredAt(LocalDateTime.now());
        order.setProviderCompleted(true);
        order.setStatus(OrderStatus.PENDING_CUSTOMER_ACCEPTANCE);
        orders.save(order);
        recordStatusChange(order, previousStatus, provider, "Provider báo đã hoàn thành công việc; chờ Customer nghiệm thu.");
    }

    @Transactional
    public void requestDeliveryRevision(Long orderId, User customer) {
        RentalOrder order = customerOrderForUpdate(orderId, customer);
        requireStatus(order, OrderStatus.PENDING_CUSTOMER_ACCEPTANCE);
        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.PROCESSING);
        order.setProviderCompleted(false);
        order.setProgressPercent(99);
        order.setDeliveredAt(null);
        order.setCustomerAcceptedCompletion(false);
        orders.save(order);
        recordStatusChange(order, previousStatus, customer, "Customer yêu cầu chỉnh sửa sản phẩm bàn giao; công việc tiếp tục.");
    }

    @Transactional
    public OtpDispatchResult customerAcceptCompletion(Long orderId, User customer) {
        RentalOrder order = customerOrderForUpdate(orderId, customer);
        requireStatus(order, OrderStatus.PENDING_CUSTOMER_ACCEPTANCE);
        if (!Boolean.TRUE.equals(order.getProviderCompleted())) {
            throw new IllegalStateException("Provider chưa gửi bàn giao hoàn chỉnh để Customer nghiệm thu.");
        }
        DeliverySubmission latest = deliveries.findFirstByOrderIdOrderByVersionDesc(orderId)
                .orElseThrow(() -> new IllegalStateException("Chưa có bản bàn giao để nghiệm thu."));
        if (!"SUBMITTED".equals(latest.getStatus())
                || (latest.getFiles().isEmpty() && latest.getLinks().isEmpty())) {
            throw new IllegalStateException("Chỉ có thể nghiệm thu bản bàn giao mới nhất có sản phẩm hợp lệ.");
        }
        // The email must be deliverable before acceptance can move to OTP waiting.
        return issueCompletionOtp(order, customer, true);
    }

    @Transactional
    public OtpDispatchResult resendCompletionOtp(Long orderId, User customer) {
        RentalOrder order = customerOrderForUpdate(orderId, customer);
        requireStatus(order, OrderStatus.PENDING_PAYMENT_OTP);
        if (!Boolean.TRUE.equals(order.getCustomerAcceptedCompletion())) {
            throw new IllegalStateException("Customer chưa nghiệm thu đơn hàng.");
        }
        if (order.getCompletionOtpAttempts() != null
                && order.getCompletionOtpAttempts() >= COMPLETION_OTP_MAX_ATTEMPTS) {
            throw new IllegalStateException("Đã nhập sai OTP quá 5 lần. Hãy liên hệ hỗ trợ để xác minh lại.");
        }
        if (order.getCompletionOtpResendCount() != null
                && order.getCompletionOtpResendCount() >= COMPLETION_OTP_MAX_RESENDS) {
            throw new IllegalStateException("Đã đạt giới hạn gửi lại OTP. Hãy liên hệ hỗ trợ.");
        }
        LocalDateTime lastSent = order.getCompletionOtpLastSentAt();
        if (lastSent != null && LocalDateTime.now().isBefore(lastSent.plusSeconds(COMPLETION_OTP_RESEND_WAIT_SECONDS))) {
            throw new IllegalStateException("Vui lòng đợi ít nhất 60 giây trước khi gửi lại OTP.");
        }
        return issueCompletionOtp(order, customer, false);
    }

    /**
     * OTP is always delivered to Customer's email; no OTP code is exposed in logs or HTML.
     * If SMTP fails, throw and roll back the status/hash change, keeping escrow locked.
     */
    private OtpDispatchResult issueCompletionOtp(RentalOrder order, User customer, boolean firstIssue) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || smtpFromAddress == null || smtpFromAddress.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình tài khoản SMTP. Không thể gửi OTP quyết toán qua email.");
        }
        LocalDateTime now = LocalDateTime.now();
        String code;
        do {
            code = String.format("%06d", RANDOM.nextInt(1_000_000));
        } while (!firstIssue && order.getCompletionOtpHash() != null
                && passwordEncoder.matches(code, order.getCompletionOtpHash()));
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(smtpFromAddress.trim());
        mail.setTo(customer.getEmail());
        mail.setSubject("Mã OTP xác nhận quyết toán SkillShare - DH" + order.getId());
        mail.setText("Mã OTP quyết toán đơn DH" + order.getId() + " là: " + code
                + "\nMã có hiệu lực trong 10 phút. Chỉ nhập mã khi bạn đã nghiệm thu sản phẩm."
                + "\nNếu bạn không yêu cầu quyết toán, hãy bỏ qua email này.");
        try {
            sender.send(mail);
        } catch (Exception e) {
            log.warn("Không gửi được email OTP quyết toán cho DH{}: {}", order.getId(), e.getClass().getSimpleName());
            throw new IllegalStateException("Không gửi được OTP tới email của Customer. Vui lòng kiểm tra SMTP và thử lại.", e);
        }
        OrderStatus previousStatus = order.getStatus();
        order.setCompletionOtpHash(passwordEncoder.encode(code));
        order.setCompletionOtpExpiresAt(now.plusMinutes(10));
        order.setCompletionOtpLastSentAt(now);
        if (firstIssue) {
            order.setCompletionOtpAttempts(0);
            order.setCompletionOtpResendCount(0);
            order.setCustomerAcceptedCompletion(true);
            order.setStatus(OrderStatus.PENDING_PAYMENT_OTP);
        } else {
            order.setCompletionOtpResendCount((order.getCompletionOtpResendCount() == null
                    ? 0 : order.getCompletionOtpResendCount()) + 1);
        }
        orders.save(order);
        if (firstIssue) {
            recordStatusChange(order, previousStatus, customer,
                    "Customer nghiệm thu; OTP đã gửi tới email. Chờ xác minh mới giải ngân.");
        }
        return new OtpDispatchResult(true, null);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyOtpAndSettle(Long orderId, User customer, String otp) {
        RentalOrder order = customerOrderForUpdate(orderId, customer);
        requireStatus(order, OrderStatus.PENDING_PAYMENT_OTP);
        if (!Boolean.TRUE.equals(order.getCustomerAcceptedCompletion())
                || !Boolean.TRUE.equals(order.getProviderCompleted())) {
            throw new IllegalStateException("Đơn chưa có đủ xác nhận bàn giao và nghiệm thu.");
        }
        DeliverySubmission latest = deliveries.findFirstByOrderIdOrderByVersionDesc(orderId)
                .orElseThrow(() -> new IllegalStateException("Chưa có bản bàn giao để quyết toán."));
        if (!"ACCEPTED".equals(latest.getStatus())) {
            throw new IllegalStateException("Chưa có bản bàn giao mới nhất được Customer nghiệm thu.");
        }
        if (order.getCompletionOtpAttempts() != null
                && order.getCompletionOtpAttempts() >= COMPLETION_OTP_MAX_ATTEMPTS) {
            throw new IllegalStateException("Đã nhập sai OTP quá 5 lần. Hãy liên hệ hỗ trợ để xác minh lại.");
        }
        if (order.getCompletionOtpHash() == null || order.getCompletionOtpExpiresAt() == null
                || !order.getCompletionOtpExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("OTP đã hết hạn hoặc chưa được phát hành. Hãy yêu cầu mã mới.");
        }
        if (otp == null || !otp.trim().matches("[0-9]{6}")
                || !passwordEncoder.matches(otp.trim(), order.getCompletionOtpHash())) {
            order.setCompletionOtpAttempts((order.getCompletionOtpAttempts() == null
                    ? 0 : order.getCompletionOtpAttempts()) + 1);
            orders.save(order);
            throw new IllegalArgumentException("Mã OTP không đúng.");
        }
        settle(order, safe(order.getTotal()), true, customer);
    }

    /** Customer can freely cancel before BOTH start confirmations. All held funds return to the wallet. */
    @Transactional
    public void cancelByCustomer(Long orderId, User customer) {
        cancelByCustomer(orderId, customer, null);
    }

    @Transactional
    public void cancelByCustomer(Long orderId, User customer, String reason) {
        RentalOrder order = customerOrderForUpdate(orderId, customer);
        if (isTerminal(order)) throw new IllegalStateException("Đơn đã kết thúc, không thể hủy.");
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT_OTP) {
            throw new IllegalStateException("Đơn đang chờ OTP quyết toán; cần xử lý qua hỗ trợ, không thể hủy trực tiếp.");
        }
        if (order.getStatus() == OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER
                || order.getStatus() == OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER
                || order.getStatus() == OrderStatus.CANCELLATION_DISPUTED) {
            throw new IllegalStateException("Đơn đã có yêu cầu hủy/tranh chấp; không được gửi lại để tránh xử lý hai lần.");
        }
        if (order.getSettledAt() != null || settlements.existsByOrderId(orderId)) {
            throw new IllegalStateException("Đơn đã quyết toán, không thể xử lý yêu cầu hủy lần nữa.");
        }
        String customerReason = reason == null || reason.isBlank()
                ? "Customer yêu cầu hủy đơn hàng." : CancellationCompensationPolicy.description(reason, 1, 500, "Lý do hủy");
        boolean beforeStart = order.getStatus() == OrderStatus.PENDING_PROVIDER_ACCEPTANCE
                || order.getStatus() == OrderStatus.PENDING_START_CONFIRMATION;
        boolean afterStart = order.getStatus() == OrderStatus.PROCESSING
                || order.getStatus() == OrderStatus.PENDING_CUSTOMER_ACCEPTANCE;
        if (!beforeStart && !afterStart) {
            throw new IllegalStateException("Trạng thái hiện tại không hỗ trợ hủy trực tiếp; cần Admin hỗ trợ đơn cũ.");
        }
        // Prevent silent cancellation of a legacy/non-escrow order or a corrupted hold.
        if (safe(order.getLockedAmount()).compareTo(validHoldAmount(order.getTotal())) != 0) {
            throw new IllegalStateException("Tiền ký quỹ không khớp hợp đồng; cần Admin đối soát trước khi hủy.");
        }
        order.setCancellationCustomerReason(customerReason);
        order.setCancellationReason(customerReason);
        order.setCancellationStartedAt(LocalDateTime.now());
        if (beforeStart) {
            if (order.getWorkStartedAt() != null
                    || (Boolean.TRUE.equals(order.getCustomerStarted())
                    && Boolean.TRUE.equals(order.getProviderStarted()))) {
                throw new IllegalStateException("Đơn đã bắt đầu, không thể áp dụng hoàn tiền trước khi làm.");
            }
            cancelAndRelease(order, customer,
                    "Customer hủy trước khi hai bên xác nhận bắt đầu: hoàn khóa toàn bộ ký quỹ. Lý do: " + customerReason);
            return;
        }
        if (!Boolean.TRUE.equals(order.getCustomerStarted())
                || !Boolean.TRUE.equals(order.getProviderStarted())) {
            throw new IllegalStateException("Trạng thái xác nhận bắt đầu không nhất quán; cần đối soát.");
        }
        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER);
        orders.save(order);
        recordStatusChange(order, previousStatus, customer,
                "Customer yêu cầu hủy sau khi bắt đầu; escrow giữ nguyên, chờ Provider đề xuất bằng chứng và chi phí.");
    }

    @Transactional
    public void cancelByProvider(Long orderId, User provider, String reason) {
        // Pessimistic lock serializes cancellation with Customer OTP settlement and dispute resolution.
        RentalOrder order = providerOrderForUpdate(orderId, provider);
        String why = (reason == null || reason.isBlank()) ? "Provider chủ động hủy."
                : CancellationCompensationPolicy.description(reason, 1, 500, "Lý do hủy của Provider");
        refundAndCloseProviderCancellation(order, provider, why);
    }

    /**
     * A Provider-initiated cancellation must never pay a commission or Provider compensation.
     * Even when Customer has already requested cancellation, disputed compensation, or
     * approved delivery (but has NOT settled via OTP), the full outstanding escrow is refunded.
     * Order, wallet, ledger snapshot, booking and history commit atomically.
     */
    private void refundAndCloseProviderCancellation(RentalOrder order, User provider, String reason) {
        BigDecimal refund = ProviderCancellationPolicy.refundableAmount(
                order.getStatus(), order.getLockedAmount(), order.getTotal(),
                order.getSettledAt() != null || settlements.existsByOrderId(order.getId()));
        UserWallet customerWallet = wallets.findByUserForUpdate(order.getCustomer())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy ví Customer; cần đối soát trước khi hoàn tiền."));
        BigDecimal lockedInWallet = safe(customerWallet.getLockedAmount());
        if (lockedInWallet.compareTo(refund) < 0 || safe(customerWallet.getBalance()).signum() < 0) {
            throw new IllegalStateException("Số tiền ký quỹ không khớp; cần Admin đối soát trước khi hoàn tiền.");
        }

        OrderStatus previousStatus = order.getStatus();
        LocalDateTime now = LocalDateTime.now();
        customerWallet.setLockedAmount(lockedInWallet.subtract(refund));
        customerWallet.setBalance(safe(customerWallet.getBalance()).add(refund));
        wallets.save(customerWallet);
        record(customerWallet, order, "UNHOLD_REFUND", refund,
                "Provider hủy DH" + order.getId() + "; hoàn đủ 100% ký quỹ; không thu phí.");

        // One accounting snapshot per order; the starting 1/3 was only earmarked, not paid out.
        EscrowSettlement snapshot = EscrowSettlement.builder()
                .order(order).settledBy(provider).kind("PROVIDER_CANCELLATION")
                .heldBefore(refund).grossAmount(ZERO).customerRefundAmount(refund)
                .platformFeeAmount(ZERO).providerNetAmount(ZERO).commissionRate(ZERO)
                .settledAt(now).build();
        if (!snapshot.isBalanced()) throw new IllegalStateException("Sổ hoàn tiền không cân bằng.");
        settlements.save(snapshot);

        order.setLockedAmount(ZERO);
        order.setProviderNetAmount(ZERO);
        order.setPlatformFeeAmount(ZERO);
        order.setSettledAt(now);
        closeStartMilestone(order);
        order.setCompletionOtpHash(null);
        order.setCompletionOtpExpiresAt(null);
        order.setCompletionOtpLastSentAt(null);
        order.setCompletionOtpAttempts(0);
        order.setCompletionOtpResendCount(0);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledBy(provider);
        order.setCancelledAt(now);
        // Preserve Customer's original cancellation explanation in cancellationCustomerReason;
        // cancellationReason must describe who ACTUALLY ended the order.
        order.setCancellationReason("Provider hủy và hoàn 100% ký quỹ: " + reason);
        if (order.getBooking() != null) {
            order.getBooking().setStatus(BookingStatus.CANCELLED);
            order.getBooking().setCancelledAt(now);
            order.getBooking().setCancelledBy(provider);
            order.getBooking().setCancellationReason(order.getCancellationReason());
            bookings.save(order.getBooking());
        }
        orders.save(order);
        recordStatusChange(order, previousStatus, provider,
                "Provider hủy; Customer được hoàn " + refund.toPlainString()
                        + "đ (100% tiền ký quỹ), phí 0đ, Provider nhận 0đ.");
    }

    /** Provider must explain completed work and identify its evidence before asking for a payout. */
    @Transactional
    public void proposeCancellationCompensation(Long orderId, User provider,
                                                  BigDecimal amount, String note, String evidence) {
        RentalOrder order = providerOrderForUpdate(orderId, provider);
        requireStatus(order, OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER);
        if (order.getSettledAt() != null || settlements.existsByOrderId(orderId)) {
            throw new IllegalStateException("Đơn đã được quyết toán.");
        }
        BigDecimal proposed = CancellationCompensationPolicy.amount(amount, order.getLockedAmount());
        String summary = CancellationCompensationPolicy.description(note, 20, 2000, "Mô tả phần việc");
        String proof = CancellationCompensationPolicy.description(evidence, 10, 2000, "Bằng chứng công việc");
        OrderStatus previousStatus = order.getStatus();
        order.setCancellationCompensation(proposed);
        order.setCancellationWorkSummary(summary);
        order.setCancellationEvidence(proof);
        order.setCancellationProposedAt(LocalDateTime.now());
        order.setStatus(OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER);
        orders.save(order);
        recordStatusChange(order, previousStatus, provider,
                "Provider gửi đề xuất chi phí " + proposed.toPlainString() + "đ kèm mô tả/bằng chứng; chưa giải ngân.");
    }

    /** Legacy overload retained only for compilation: require evidence in new submissions. */
    @Transactional
    public void proposeCancellationCompensation(Long orderId, User provider, BigDecimal amount, String note) {
        proposeCancellationCompensation(orderId, provider, amount, note, null);
    }

    @Transactional
    public void customerResolveCompensation(Long orderId, User customer, boolean accept) {
        customerResolveCompensation(orderId, customer, accept, null);
    }

    @Transactional
    public void customerResolveCompensation(Long orderId, User customer, boolean accept, String reason) {
        RentalOrder order = customerOrderForUpdate(orderId, customer);
        requireStatus(order, OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER);
        OrderStatus previousStatus = order.getStatus();
        if (accept) {
            if (order.getCancellationCompensation() == null) {
                throw new IllegalStateException("Provider chưa cung cấp số tiền bồi hoàn hợp lệ.");
            }
            settle(order, CancellationCompensationPolicy.amount(
                    order.getCancellationCompensation(), order.getLockedAmount()), false, customer);
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
            recordStatusChange(order, previousStatus, customer,
                    "Customer chấp thuận bồi hoàn; khoản đã thống nhất được quyết toán, phần dư hoàn về ví.");
        } else {
            String explanation = CancellationCompensationPolicy.description(reason, 10, 1000, "Lý do phản đối");
            // NEVER resume work or release funds just because the proposal was rejected.
            order.setStatus(OrderStatus.CANCELLATION_DISPUTED);
            order.setCancellationDisputeReason(explanation);
            order.setCancellationDisputedAt(LocalDateTime.now());
            orders.save(order);
            recordStatusChange(order, previousStatus, customer,
                    "Customer phản đối chi phí hủy; tiền giữ nguyên, chờ hai bên hoặc Admin giải quyết.");
            notifications.notifyAdmins("ESCROW_DISPUTE", "Tranh chấp chi phí hủy đơn #" + order.getId(),
                    "Customer phản đối chi phí hủy của Provider; cần xem bằng chứng và xử lý thủ công.",
                    "ORDER", order.getId(), "/admin/orders");
        }
    }

    /**
     * Atomic escrow settlement. The 1/3 starting milestone is an earmark only;
     * it is NOT debited or paid separately here.
     *
     * Both wallets, the one-per-order accounting snapshot and all wallet transaction
     * rows are committed in the same database transaction as the order transition.
     */
    private void settle(RentalOrder order, BigDecimal providerAmount, boolean completed, User actor) {
        if (order == null || order.getId() == null || order.getSettledAt() != null
                || settlements.existsByOrderId(order.getId())) {
            throw new IllegalStateException("Đơn đã được quyết toán; không thể chi trả lần hai.");
        }
        if (order.getCustomer() == null || order.getProvider() == null
                || order.getCustomer().getId() == null || order.getProvider().getId() == null
                || order.getCustomer().getId().equals(order.getProvider().getId())) {
            throw new IllegalStateException("Đơn thiếu thông tin hai bên nhận/chuyển tiền hợp lệ.");
        }
        if (completed && order.getStatus() != OrderStatus.PENDING_PAYMENT_OTP) {
            throw new IllegalStateException("Chỉ quyết toán đơn đã nghiệm thu và xác minh OTP.");
        }
        if (!completed && order.getStatus() != OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER) {
            throw new IllegalStateException("Chưa có thỏa thuận bồi hoàn được Customer xác nhận.");
        }
        EscrowSettlementMath amounts = EscrowSettlementMath.calculate(
                order.getLockedAmount(), providerAmount, commissionPolicy.rate());
        if (completed && amounts.gross().compareTo(amounts.held()) != 0) {
            throw new IllegalStateException("Quyết toán hoàn thành phải sử dụng đủ khoản ký quỹ của đơn.");
        }
        if (completed && amounts.gross().compareTo(order.getTotal()) != 0) {
            throw new IllegalStateException("Ký quỹ của đơn không khớp với giá trị hợp đồng.");
        }

        UserWallet customerWallet = lockWallet(order.getCustomer());
        UserWallet providerWallet = lockWallet(order.getProvider());
        if (customerWallet.getId().equals(providerWallet.getId())) {
            throw new IllegalStateException("Không thể chuyển khoản ký quỹ vào cùng ví Customer.");
        }
        if (safe(customerWallet.getLockedAmount()).compareTo(amounts.held()) < 0) {
            throw new IllegalStateException("Số dư ký quỹ không khớp với đơn hàng; đã chặn quyết toán để bảo vệ tiền.");
        }
        if (safe(customerWallet.getBalance()).signum() < 0 || safe(providerWallet.getBalance()).signum() < 0) {
            throw new IllegalStateException("Số dư ví bất thường; cần Admin đối soát trước khi quyết toán.");
        }
        customerWallet.setLockedAmount(safe(customerWallet.getLockedAmount()).subtract(amounts.held()));
        customerWallet.setBalance(safe(customerWallet.getBalance()).add(amounts.refund()));
        providerWallet.setBalance(safe(providerWallet.getBalance()).add(amounts.providerNet()));
        wallets.save(customerWallet);
        wallets.save(providerWallet);
        // Ledger quantities deliberately distinguish held funds from spendable balance.
        record(customerWallet, order, completed ? "SETTLEMENT_OUT" : "CANCELLATION_REFUND",
                amounts.gross().negate(), completed
                ? "Quyết toán DH" + order.getId() + " sau OTP Customer."
                : "Chi trả phần việc theo thỏa thuận hủy DH" + order.getId());
        if (amounts.refund().signum() > 0) {
            record(customerWallet, order, "UNHOLD_REFUND", amounts.refund(),
                    "Hoàn phần ký quỹ còn lại của đơn DH" + order.getId());
        }
        if (amounts.providerNet().signum() > 0) {
            record(providerWallet, order, "SETTLEMENT_IN", amounts.providerNet(),
                    "Provider thực nhận từ DH" + order.getId() + "; phí sàn: "
                    + amounts.fee().toPlainString() + "đ; giá trị tính phí: "
                    + amounts.gross().toPlainString() + "đ.");
        }

        // Immutable, one-row-per-order accounting record with DB unique constraint.
        // Platform fee is recorded here; it is NOT an extra debit from Customer.
        LocalDateTime settledTime = LocalDateTime.now();
        EscrowSettlement snapshot = EscrowSettlement.builder()
                .order(order).settledBy(actor)
                .kind(completed ? "COMPLETION" : "CUSTOMER_CANCELLATION")
                .heldBefore(amounts.held()).grossAmount(amounts.gross())
                .customerRefundAmount(amounts.refund()).platformFeeAmount(amounts.fee())
                .providerNetAmount(amounts.providerNet()).commissionRate(amounts.rate())
                .settledAt(settledTime).build();
        if (!snapshot.isBalanced()) throw new IllegalStateException("Sổ quyết toán không cân bằng.");
        settlements.save(snapshot);

        order.setPlatformFeeAmount(amounts.fee());
        order.setProviderNetAmount(amounts.providerNet());
        order.setLockedAmount(ZERO);
        closeStartMilestone(order);
        order.setSettledAt(settledTime);
        order.setCompletionOtpHash(null);
        order.setCompletionOtpExpiresAt(null);
        order.setCompletionOtpLastSentAt(null);
        if (completed) {
            OrderStatus previousStatus = order.getStatus();
            order.setStatus(OrderStatus.COMPLETED);
            order.setCompletedAt(settledTime);
            if (order.getBooking() != null) {
                order.getBooking().setStatus(BookingStatus.COMPLETED);
                bookings.save(order.getBooking());
            }
            recordStatusChange(order, previousStatus, actor,
                    "Customer xác minh OTP; escrow đã quyết toán một lần, phí và thu nhập đã ghi sổ.");
        }
        orders.save(order);
    }

    private void cancelAndRelease(RentalOrder order, User actor, String reason) {
        OrderStatus previousStatus = order.getStatus();
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
        closeStartMilestone(order);
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
        recordStatusChange(order, previousStatus, actor, reason);
    }

    /** Marks the earmark resolved; the value is already included in the ordinary escrow settlement. */
    private void closeStartMilestone(RentalOrder order) {
        if (order.getStartDepositState() == StartDepositState.RESERVED) {
            order.setStartDepositState(StartDepositState.CLOSED);
            order.setStartDepositClosedAt(LocalDateTime.now());
        }
    }

    private void recordStatusChange(RentalOrder order, OrderStatus previousStatus, User actor, String note) {
        orderHistory.save(RentalOrderStatusHistory.builder().order(order)
                .previousStatus(previousStatus == null ? null : previousStatus.name())
                .newStatus(order.getStatus().name()).changedBy(actor)
                .note(note != null && note.length() > 1000 ? note.substring(0, 1000) : note).build());
        events.publishEvent(new OrderStatusChangedEvent(order.getId(),
                previousStatus == null ? null : previousStatus.name(),
                order.getStatus().name(), actor == null ? null : actor.getId()));
    }

    private UserWallet lockWallet(User user) {
        return wallets.findByUserForUpdate(user).orElseGet(() -> wallet(user));
    }

    private void record(UserWallet wallet, RentalOrder order, String type, BigDecimal amount, String description) {
        transactions.save(WalletTransaction.builder().wallet(wallet).order(order).type(type).amount(amount)
                .balanceAfter(wallet.getBalance()).lockedAfter(wallet.getLockedAmount())
                .description(description.length() > 800 ? description.substring(0, 800) : description).build());
    }
    private RentalOrder customerOrderForUpdate(Long id, User customer) {
        RentalOrder order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (customer == null || customer.getRole() != Role.CUSTOMER || customer.getId() == null
                || order.getCustomer() == null
                || !order.getCustomer().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền thao tác đơn hàng này.");
        }
        return order;
    }

    private RentalOrder providerOrderForUpdate(Long id, User provider) {
        RentalOrder order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (provider == null || provider.getRole() != Role.PROVIDER || provider.getId() == null
                || order.getProvider() == null
                || !order.getProvider().getId().equals(provider.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền thao tác đơn hàng này.");
        }
        return order;
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
