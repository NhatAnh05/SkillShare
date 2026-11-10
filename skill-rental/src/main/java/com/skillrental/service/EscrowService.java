package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service @RequiredArgsConstructor
public class EscrowService {
    private final EscrowRepository escrows;
    private final RentalOrderRepository orders;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository paymentAttempts;
    private final RentalOrderStatusHistoryRepository history;
    private final WalletService wallet;
    private final OtpService otp;
    private final MailService mail;

    @Transactional(readOnly = true)
    public Optional<Escrow> find(RentalOrder order) {
        return escrows.findByOrder(order);
    }

    @Transactional(readOnly = true)
    public Escrow requireEscrow(RentalOrder order) {
        return escrows.findByOrder(order)
                .orElseThrow(() -> new IllegalArgumentException("Đơn hàng chưa có escrow."));
    }

    @Transactional(readOnly = true)
    public BigDecimal remaining(RentalOrder order) {
        return escrows.findByOrder(order)
                .map(e -> WalletService.nz(e.getTotalAmount()).subtract(WalletService.nz(e.getReleasedAmount())))
                .orElse(BigDecimal.ZERO);
    }

    @Transactional
    public Escrow createHold(RentalOrder order, BigDecimal amount) {
        BigDecimal value = WalletService.nz(amount).setScale(2, RoundingMode.HALF_UP);
        wallet.hold(order.getCustomer(), value, order.getId(),
                "Khóa tiền đặt đơn #SR-" + order.getId());
        return escrows.save(Escrow.builder()
                .order(order)
                .customer(order.getCustomer())
                .provider(order.getProvider())
                .totalAmount(value)
                .releasedAmount(BigDecimal.ZERO)
                .customerStartConfirmed(false)
                .providerStartConfirmed(false)
                .advanceReleased(false)
                .status(EscrowStatus.HELD)
                .build());
    }

    @Transactional
    public Escrow confirmStart(RentalOrder order, User actor) {
        Escrow escrow = requireEscrow(order);
        String previousStatus = order.getStatus() == null ? null : order.getStatus().name();
        if (escrow.getStatus() != EscrowStatus.HELD && escrow.getStatus() != EscrowStatus.STARTED) {
            throw new IllegalArgumentException("Đơn hàng không ở trạng thái có thể xác nhận bắt đầu.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (Objects.equals(actor.getId(), escrow.getCustomer().getId())) {
            if (Boolean.TRUE.equals(escrow.getCustomerStartConfirmed())) {
                return escrow;
            }
            escrow.setCustomerStartConfirmed(true);
            escrow.setCustomerStartConfirmedAt(now);
        } else if (Objects.equals(actor.getId(), escrow.getProvider().getId())) {
            if (Boolean.TRUE.equals(escrow.getProviderStartConfirmed())) {
                return escrow;
            }
            escrow.setProviderStartConfirmed(true);
            escrow.setProviderStartConfirmedAt(now);
        } else {
            throw new IllegalArgumentException("Bạn không thuộc đơn hàng này.");
        }

        if (Boolean.TRUE.equals(escrow.getCustomerStartConfirmed())
                && Boolean.TRUE.equals(escrow.getProviderStartConfirmed())
                && !Boolean.TRUE.equals(escrow.getAdvanceReleased())) {
            BigDecimal advance = WalletService.nz(escrow.getTotalAmount())
                    .divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
            if (advance.signum() > 0) {
                wallet.payHeldToProvider(escrow.getCustomer(), escrow.getProvider(), advance,
                        order.getId(), WalletTransactionType.ADVANCE_PAYMENT,
                        "Tạm ứng 1/3 giá trị đơn #SR-" + order.getId() + " khi hai bên xác nhận bắt đầu");
                escrow.setReleasedAmount(WalletService.nz(escrow.getReleasedAmount()).add(advance));
            }
            escrow.setAdvanceReleased(true);
            escrow.setStartedAt(now);
            escrow.setStatus(EscrowStatus.STARTED);
            order.setStatus(OrderStatus.PROCESSING);
            orders.save(order);
            Booking booking = order.getBooking();
            if (booking != null) {
                booking.setStatus(BookingStatus.IN_PROGRESS);
                bookings.save(booking);
            }
            recordHistory(order, previousStatus, OrderStatus.PROCESSING.name(), actor,
                    "Hai bên xác nhận bắt đầu; giải ngân tạm ứng 1/3 (" + advance + "đ).");
        } else {
            recordHistory(order, previousStatus, previousStatus, actor,
                    "Đã ghi nhận xác nhận bắt đầu từ một bên, chờ bên còn lại.");
        }
        return escrows.save(escrow);
    }

    @Transactional
    public Escrow providerComplete(RentalOrder order, User provider) {
        Escrow escrow = requireEscrow(order);
        if (!Objects.equals(provider.getId(), escrow.getProvider().getId())) {
            throw new IllegalArgumentException("Bạn không phải Provider của đơn hàng này.");
        }
        if (escrow.getStatus() != EscrowStatus.STARTED) {
            throw new IllegalArgumentException("Đơn hàng chưa bắt đầu hoặc đã ở trạng thái khác.");
        }
        if (escrow.getProviderCompletedAt() != null) {
            return escrow;
        }
        String previousStatus = order.getStatus() == null ? null : order.getStatus().name();
        escrow.setProviderCompletedAt(LocalDateTime.now());
        escrow.setStatus(EscrowStatus.AWAITING_CONFIRMATION);
        escrow.setCustomerStartConfirmed(true);
        escrow.setProviderStartConfirmed(true);
        escrows.save(escrow);

        order.setStatus(OrderStatus.AWAITING_CONFIRMATION);
        orders.save(order);
        recordHistory(order, previousStatus, OrderStatus.AWAITING_CONFIRMATION.name(), provider,
                "Provider báo hoàn thành công việc; chờ khách nghiệm thu và xác nhận thanh toán.");
        return escrow;
    }

    @Transactional
    public String requestFinalOtp(RentalOrder order, User customer) {
        Escrow escrow = requireEscrow(order);
        if (!Objects.equals(customer.getId(), escrow.getCustomer().getId())) {
            throw new IllegalArgumentException("Bạn không phải khách hàng của đơn này.");
        }
        if (escrow.getStatus() != EscrowStatus.AWAITING_CONFIRMATION) {
            throw new IllegalArgumentException("Đơn hàng chưa sẵn sàng để xác nhận thanh toán.");
        }
        if (escrow.getProviderCompletedAt() == null) {
            throw new IllegalArgumentException("Provider chưa báo hoàn thành công việc.");
        }
        return otp.issue(order, customer, "thanh toán đơn #SR-" + order.getId());
    }

    @Transactional
    public Escrow finalizePayment(RentalOrder order, User customer, String code) {
        Escrow escrow = requireEscrow(order);
        if (!Objects.equals(customer.getId(), escrow.getCustomer().getId())) {
            throw new IllegalArgumentException("Bạn không phải khách hàng của đơn này.");
        }
        if (escrow.getStatus() != EscrowStatus.AWAITING_CONFIRMATION) {
            throw new IllegalArgumentException("Đơn hàng chưa sẵn sàng để thanh toán.");
        }
        if (!otp.verify(order, customer, code)) {
            throw new IllegalArgumentException("Mã OTP không đúng hoặc đã hết hạn. Sau 5 lần nhập sai, mã sẽ bị vô hiệu hóa và bạn cần bấm \"Gửi lại mã OTP\".");
        }

        BigDecimal total = WalletService.nz(escrow.getTotalAmount());
        BigDecimal remaining = total.subtract(WalletService.nz(escrow.getReleasedAmount()));
        if (remaining.signum() > 0) {
            wallet.payHeldToProvider(escrow.getCustomer(), escrow.getProvider(), remaining,
                    order.getId(), WalletTransactionType.FINAL_PAYMENT,
                    "Tất toán phần còn lại của đơn #SR-" + order.getId() + " sau nghiệm thu");
        }
        String previousStatus = order.getStatus() == null ? null : order.getStatus().name();
        escrow.setReleasedAmount(total);
        escrow.setCustomerAcceptedAt(LocalDateTime.now());
        escrow.setStatus(EscrowStatus.RELEASED);
        escrows.save(escrow);

        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        orders.save(order);
        Booking booking = order.getBooking();
        if (booking != null) {
            booking.setStatus(BookingStatus.COMPLETED);
            bookings.save(booking);
        }
        markPaid(order, total);
        recordHistory(order, previousStatus, OrderStatus.COMPLETED.name(), customer,
                "Khách nhập OTP xác nhận; hệ thống tất toán toàn bộ tiền escrow cho Provider.");
        return escrow;
    }

    @Transactional
    public Escrow cancelBeforeStart(RentalOrder order, User actor, String reason) {
        Escrow escrow = requireEscrow(order);
        if (escrow.getStatus() != EscrowStatus.HELD) {
            throw new IllegalArgumentException("Đơn đã bắt đầu hoặc đã kết thúc, không thể hủy theo cách này.");
        }
        BigDecimal remaining = WalletService.nz(escrow.getTotalAmount())
                .subtract(WalletService.nz(escrow.getReleasedAmount()));
        if (remaining.signum() > 0) {
            wallet.releaseHold(escrow.getCustomer(), remaining, order.getId(),
                    "Hoàn khóa tiền đơn #SR-" + order.getId() + " do hủy trước khi bắt đầu");
        }
        String previousStatus = order.getStatus() == null ? null : order.getStatus().name();
        escrow.setStatus(EscrowStatus.CANCELLED_REFUNDED);
        escrow.setCancelledAt(LocalDateTime.now());
        escrow.setCancelledBy(actor);
        escrow.setCancellationReason(reason);
        escrows.save(escrow);

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(actor);
        order.setCancellationReason(reason);
        orders.save(order);
        Booking booking = order.getBooking();
        if (booking != null) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelledBy(actor);
            booking.setCancelledAt(LocalDateTime.now());
            booking.setCancellationReason(reason);
            bookings.save(booking);
        }
        recordHistory(order, previousStatus, OrderStatus.CANCELLED.name(), actor,
                "Hủy trước khi bắt đầu; hoàn 100% tiền đã khóa về ví khách.");
        return escrow;
    }

    @Transactional
    public Escrow requestCancellationAfterStart(RentalOrder order, User customer, String reason) {
        Escrow escrow = requireEscrow(order);
        if (!Objects.equals(customer.getId(), escrow.getCustomer().getId())) {
            throw new IllegalArgumentException("Bạn không phải khách hàng của đơn này.");
        }
        if (escrow.getStatus() != EscrowStatus.STARTED) {
            throw new IllegalArgumentException("Chỉ có thể yêu cầu hủy sau khi hai bên đã xác nhận bắt đầu.");
        }
        String previousStatus = order.getStatus() == null ? null : order.getStatus().name();
        escrow.setStatus(EscrowStatus.CANCELLATION_REQUESTED);
        escrow.setCancelledBy(customer);
        escrow.setCancellationReason(reason);
        escrows.save(escrow);

        order.setStatus(OrderStatus.CANCELLATION_REQUESTED);
        orders.save(order);
        mail.sendNotification(escrow.getProvider().getEmail(),
                "SkillShare - Khách yêu cầu hủy đơn #SR-" + order.getId(),
                "Khách hàng " + customer.getFullName() + " yêu cầu hủy đơn #SR-" + order.getId()
                        + ".\nLý do: " + reason
                        + "\nVui lòng vào mục Đơn hàng của Provider để mô tả phần việc đã làm và đề xuất mức chi trả.");
        recordHistory(order, previousStatus, OrderStatus.CANCELLATION_REQUESTED.name(), customer,
                "Khách yêu cầu hủy sau khi đã bắt đầu; chờ Provider mô tả công việc và đề xuất chi trả.");
        return escrow;
    }

    @Transactional
    public Escrow providerCancel(RentalOrder order, User provider, String reason) {
        Escrow escrow = requireEscrow(order);
        if (!Objects.equals(provider.getId(), escrow.getProvider().getId())) {
            throw new IllegalArgumentException("Bạn không phải Provider của đơn này.");
        }
        if (escrow.getStatus() != EscrowStatus.STARTED
                && escrow.getStatus() != EscrowStatus.CANCELLATION_REQUESTED) {
            throw new IllegalArgumentException("Đơn hàng không ở trạng thái có thể hủy.");
        }

        BigDecimal total = WalletService.nz(escrow.getTotalAmount());
        BigDecimal released = WalletService.nz(escrow.getReleasedAmount());
        BigDecimal remaining = total.subtract(released);

        if (remaining.signum() > 0) {
            wallet.releaseHold(escrow.getCustomer(), remaining, order.getId(),
                    "Provider hủy đơn #SR-" + order.getId() + "; hoàn phần tiền còn khóa");
        }
        if (released.signum() > 0) {
            wallet.clawbackProvider(escrow.getProvider(), released, order.getId(),
                    "Thu hồi tạm ứng do Provider hủy đơn #SR-" + order.getId());
            wallet.refundCustomer(escrow.getCustomer(), released, order.getId(),
                    "Hoàn 100% tiền đã thanh toán do Provider hủy đơn #SR-" + order.getId());
        }

        String previousStatus = order.getStatus() == null ? null : order.getStatus().name();
        escrow.setStatus(EscrowStatus.PROVIDER_CANCELLED_REFUNDED);
        escrow.setCancelledAt(LocalDateTime.now());
        escrow.setCancelledBy(provider);
        escrow.setCancellationReason(reason);
        escrow.setReleasedAmount(BigDecimal.ZERO);
        escrows.save(escrow);

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(provider);
        order.setCancellationReason(reason);
        orders.save(order);
        Booking booking = order.getBooking();
        if (booking != null) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelledBy(provider);
            booking.setCancelledAt(LocalDateTime.now());
            booking.setCancellationReason(reason);
            bookings.save(booking);
        }
        mail.sendNotification(escrow.getCustomer().getEmail(),
                "SkillShare - Provider hủy đơn #SR-" + order.getId(),
                "Provider đã hủy đơn #SR-" + order.getId() + ".\nLý do: " + reason
                        + "\nHệ thống đã hoàn 100% số tiền đã thanh toán về ví của bạn.");
        recordHistory(order, previousStatus, OrderStatus.CANCELLED.name(), provider,
                "Provider hủy đơn; hoàn 100% tiền cho khách.");
        return escrow;
    }

    @Transactional
    public Escrow respondToCancellation(RentalOrder order, User provider,
                                        String workSummary, BigDecimal proposedAmount) {
        Escrow escrow = requireEscrow(order);
        if (!Objects.equals(provider.getId(), escrow.getProvider().getId())) {
            throw new IllegalArgumentException("Bạn không phải Provider của đơn này.");
        }
        if (escrow.getStatus() != EscrowStatus.CANCELLATION_REQUESTED) {
            throw new IllegalArgumentException("Đơn hàng không có yêu cầu hủy đang chờ xử lý.");
        }
        if (workSummary == null || workSummary.isBlank()) {
            throw new IllegalArgumentException("Vui lòng mô tả phần công việc đã thực hiện.");
        }

        BigDecimal remaining = WalletService.nz(escrow.getTotalAmount())
                .subtract(WalletService.nz(escrow.getReleasedAmount()));
        BigDecimal compensation = WalletService.nz(proposedAmount).setScale(2, RoundingMode.HALF_UP);
        if (compensation.signum() < 0) {
            throw new IllegalArgumentException("Số tiền đề xuất không hợp lệ.");
        }
        if (compensation.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Số tiền đề xuất vượt quá phần escrow còn lại ("
                    + remaining + "đ).");
        }

        if (compensation.signum() > 0) {
            wallet.payHeldToProvider(escrow.getCustomer(), escrow.getProvider(), compensation,
                    order.getId(), WalletTransactionType.CANCEL_COMPENSATION,
                    "Chi trả cho công việc Provider đã làm khi khách hủy đơn #SR-" + order.getId());
        }
        BigDecimal refund = remaining.subtract(compensation);
        if (refund.signum() > 0) {
            wallet.releaseHold(escrow.getCustomer(), refund, order.getId(),
                    "Hoàn phần còn lại của đơn #SR-" + order.getId() + " sau khi trừ chi phí Provider");
        }

        String previousStatus = order.getStatus() == null ? null : order.getStatus().name();
        escrow.setWorkSummary(workSummary.trim());
        escrow.setCompensationAmount(compensation);
        escrow.setReleasedAmount(WalletService.nz(escrow.getReleasedAmount()).add(compensation));
        escrow.setCustomerAcceptedAt(LocalDateTime.now());
        escrow.setStatus(EscrowStatus.PARTIAL_REFUND);
        escrows.save(escrow);

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(escrow.getCustomer());
        orders.save(order);
        Booking booking = order.getBooking();
        if (booking != null) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelledAt(LocalDateTime.now());
            booking.setCancellationReason(escrow.getCancellationReason());
            bookings.save(booking);
        }

        mail.sendNotification(escrow.getCustomer().getEmail(),
                "SkillShare - Kết quả hủy đơn #SR-" + order.getId(),
                "Provider đã báo cáo công việc đã thực hiện cho đơn #SR-" + order.getId() + ":\n"
                        + workSummary.trim()
                        + "\nSố tiền Provider đề xuất cho phần đã làm: " + compensation + "đ."
                        + "\nHệ thống đã thanh toán số tiền này cho Provider và hoàn "
                        + refund + "đ về ví của bạn.");
        recordHistory(order, previousStatus, OrderStatus.CANCELLED.name(), provider,
                "Provider mô tả công việc và đề xuất chi trả; hệ thống thanh toán "
                        + compensation + "đ, hoàn " + refund + "đ cho khách.");
        return escrow;
    }

    /**
     * Auto-settle an order that stayed in AWAITING_CONFIRMATION too long:
     * releases the remaining escrow to the provider and completes the order.
     */
    @Transactional
    public Escrow autoRelease(RentalOrder order, String note) {
        Escrow escrow = requireEscrow(order);
        if (escrow.getStatus() != EscrowStatus.AWAITING_CONFIRMATION) {
            throw new IllegalArgumentException("Đơn hàng không ở trạng thái chờ nghiệm thu.");
        }
        BigDecimal total = WalletService.nz(escrow.getTotalAmount());
        BigDecimal remaining = total.subtract(WalletService.nz(escrow.getReleasedAmount()));
        if (remaining.signum() > 0) {
            wallet.payHeldToProvider(escrow.getCustomer(), escrow.getProvider(), remaining,
                    order.getId(), WalletTransactionType.FINAL_PAYMENT,
                    "Tự động tất toán đơn #SR-" + order.getId() + " do khách không xác nhận trong thời hạn");
        }
        String previousStatus = order.getStatus() == null ? null : order.getStatus().name();
        escrow.setReleasedAmount(total);
        escrow.setCustomerAcceptedAt(LocalDateTime.now());
        escrow.setStatus(EscrowStatus.RELEASED);
        escrows.save(escrow);

        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        orders.save(order);
        Booking booking = order.getBooking();
        if (booking != null) {
            booking.setStatus(BookingStatus.COMPLETED);
            bookings.save(booking);
        }
        markPaid(order, total);
        recordHistory(order, previousStatus, OrderStatus.COMPLETED.name(), escrow.getProvider(), note);
        mail.sendNotification(escrow.getCustomer().getEmail(),
                "SkillShare - Đã tự động tất toán đơn #SR-" + order.getId(),
                "Đơn #SR-" + order.getId() + " đã quá thời hạn xác nhận thanh toán.\n"
                        + "Hệ thống tự động tất toán phần tiền còn giữ cho Provider.\n"
                        + "Chi tiết: " + note);
        return escrow;
    }

    /**
     * Auto-resolve a cancellation request the provider never answered:
     * refunds the remaining held amount to the customer; the provider keeps what was already released.
     */
    @Transactional
    public Escrow autoRefundCancellation(RentalOrder order, String reason) {
        Escrow escrow = requireEscrow(order);
        if (escrow.getStatus() != EscrowStatus.CANCELLATION_REQUESTED) {
            throw new IllegalArgumentException("Đơn hàng không có yêu cầu hủy đang chờ xử lý.");
        }
        BigDecimal remaining = WalletService.nz(escrow.getTotalAmount())
                .subtract(WalletService.nz(escrow.getReleasedAmount()));
        if (remaining.signum() > 0) {
            wallet.releaseHold(escrow.getCustomer(), remaining, order.getId(),
                    "Tự động hoàn phần tiền còn giữ của đơn #SR-" + order.getId()
                            + " do Provider không phản hồi yêu cầu hủy");
        }
        String previousStatus = order.getStatus() == null ? null : order.getStatus().name();
        escrow.setWorkSummary("Provider không phản hồi yêu cầu hủy trong thời hạn; hệ thống tự động hoàn phần tiền còn giữ cho khách.");
        escrow.setCompensationAmount(BigDecimal.ZERO);
        escrow.setCustomerAcceptedAt(LocalDateTime.now());
        escrow.setStatus(EscrowStatus.PARTIAL_REFUND);
        escrows.save(escrow);

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(escrow.getCustomer());
        order.setCancellationReason(reason);
        orders.save(order);
        Booking booking = order.getBooking();
        if (booking != null) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelledAt(LocalDateTime.now());
            booking.setCancellationReason(reason);
            bookings.save(booking);
        }
        recordHistory(order, previousStatus, OrderStatus.CANCELLED.name(), escrow.getCustomer(), reason);
        mail.sendNotification(escrow.getCustomer().getEmail(),
                "SkillShare - Đã tự động xử lý hủy đơn #SR-" + order.getId(),
                "Yêu cầu hủy đơn #SR-" + order.getId() + " không được Provider phản hồi trong thời hạn.\n"
                        + "Hệ thống đã hoàn " + remaining + "đ (phần tiền còn giữ) về ví của bạn.");
        return escrow;
    }

    private void markPaid(RentalOrder order, BigDecimal amount) {
        Payment payment = payments.findByOrder(order).orElseGet(() -> Payment.builder().order(order).build());
        payment.setMethod("WALLET");
        payment.setGatewayName("WALLET_ESCROW");
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        Payment saved = payments.save(payment);

        int attemptNumber = paymentAttempts.findByPaymentIdOrderByAttemptNumberDesc(saved.getId()).size() + 1;
        paymentAttempts.save(PaymentAttempt.builder()
                .payment(saved)
                .attemptNumber(attemptNumber)
                .gatewayName("WALLET")
                .merchantReference("WALLET-SR-" + order.getId() + "-" + System.currentTimeMillis())
                .amount(amount)
                .currency("VND")
                .status(PaymentAttemptStatus.SUCCEEDED)
                .responseCode("00")
                .initiatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build());
    }

    private void recordHistory(RentalOrder order, String previousStatus, String newStatus,
                               User actor, String note) {
        history.save(RentalOrderStatusHistory.builder()
                .order(order)
                .previousStatus(previousStatus)
                .newStatus(newStatus == null ? "" : newStatus)
                .changedBy(actor)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional(readOnly = true)
    public List<Escrow> forProvider(User provider) {
        return escrows.findByProviderOrderByIdDesc(provider);
    }

    /** Money currently guaranteed by active escrows (held minus already released). */
    @Transactional(readOnly = true)
    public BigDecimal totalHeldInEscrow() {
        return escrows.findByStatusIn(List.of(EscrowStatus.HELD, EscrowStatus.STARTED,
                        EscrowStatus.AWAITING_CONFIRMATION, EscrowStatus.CANCELLATION_REQUESTED)).stream()
                .map(e -> WalletService.nz(e.getTotalAmount()).subtract(WalletService.nz(e.getReleasedAmount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
