package com.skillrental.controller;

import com.skillrental.entity.OrderStatus;
import com.skillrental.entity.Role;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.EscrowService;
import com.skillrental.service.OrderService;
import com.skillrental.service.VnPayWalletTopUpService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class OrderController {
    private final CurrentUserService current;
    private final OrderService orders;
    private final EscrowService escrow;
    private final VnPayWalletTopUpService walletTopUp;

    @GetMapping("/orders")
    String list(Authentication authentication, Model model) {
        var user = current.require(authentication);
        if (user.getRole() != Role.CUSTOMER) return "redirect:/workspace";
        model.addAttribute("orders", orders.list(user));
        model.addAttribute("completedCount", orders.completed(user));
        model.addAttribute("processingCount", orders.processingCount(user));
        model.addAttribute("wallet", escrow.wallet(user));
        return "orders/orders";
    }

    // Old direct payment path is intentionally disabled: all new orders use internal escrow.
    @GetMapping("/payment/{id}")
    String oldPaymentPath(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        var user = current.require(authentication);
        if (user.getRole() != Role.CUSTOMER) return "redirect:/workspace";
        ra.addFlashAttribute("info", "Đơn được ký quỹ trong ví hệ thống; chưa cần thanh toán cho Provider ngay.");
        return "redirect:/orders/" + id + "/progress";
    }

    @PostMapping("/payment/{id}")
    String blockOldPayment(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        current.require(authentication);
        ra.addFlashAttribute("error", "Luồng thanh toán trực tiếp đã tắt. Đơn được quyết toán qua Escrow sau nghiệm thu và OTP.");
        return "redirect:/orders/" + id + "/progress";
    }

    @GetMapping("/booking/complete/{id}")
    String bookingComplete(@PathVariable Long id, Authentication authentication) {
        current.require(authentication);
        return "redirect:/orders/" + id + "/progress";
    }

    @GetMapping("/orders/table")
    String table(Authentication auth, Model model) {
        var user = current.require(auth);
        model.addAttribute("orders", orders.list(user));
        return "orders/orders :: orderTable";
    }

    @GetMapping("/orders/{id}/details")
    String details(@PathVariable Long id, Authentication auth, Model model) {
        model.addAttribute("order", orders.owned(id, current.require(auth)));
        return "orders/progress :: details";
    }

    @GetMapping("/orders/{id}/progress")
    String progress(@PathVariable Long id, Authentication auth, Model model) {
        var user = current.require(auth);
        var order = orders.owned(id, user);
        model.addAttribute("order", order);
        model.addAttribute("wallet", escrow.wallet(user));
        return "orders/progress";
    }

    @PostMapping("/orders/{id}/start-confirmation")
    String confirmStart(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        var user = current.require(auth);
        try {
            escrow.confirmStart(id, user, user.getRole());
            ra.addFlashAttribute("success", "Đã ghi nhận xác nhận bắt đầu của bạn. Công việc chỉ bắt đầu khi cả hai bên xác nhận.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/orders/" + id + "/progress";
    }

    @PostMapping("/orders/{id}/cancel")
    String cancel(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        var user = current.require(auth);
        try {
            escrow.cancelByCustomer(id, user);
            ra.addFlashAttribute("success", "Đã ghi nhận yêu cầu hủy. Nếu công việc chưa bắt đầu, ký quỹ được mở khóa; nếu đã bắt đầu, Provider cần đề xuất chi phí phần việc đã làm.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/orders/" + id + "/progress";
    }

    @PostMapping("/orders/{id}/accept-completion")
    String acceptCompletion(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        var user = current.require(auth);
        try {
            EscrowService.OtpDispatchResult result = escrow.customerAcceptCompletion(id, user);
            if (result.emailSent()) ra.addFlashAttribute("success", "Đã xác nhận nghiệm thu. Mã OTP đã gửi tới email " + user.getEmail() + ".");
            else if (result.demoCode() != null) {
                ra.addFlashAttribute("info", "Chưa cấu hình SMTP gửi email. Đây là chế độ demo: mã OTP sẽ hiển thị một lần trên trang tiếp theo.");
                ra.addFlashAttribute("demoOtp", result.demoCode());
            } else {
                ra.addFlashAttribute("error", "Không gửi được OTP qua email. Hãy kiểm tra cấu hình SMTP trước khi quyết toán.");
            }
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/orders/" + id + "/progress";
    }

    @PostMapping("/orders/{id}/resend-otp")
    String resendOtp(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        var user = current.require(auth);
        try {
            EscrowService.OtpDispatchResult result = escrow.resendCompletionOtp(id, user);
            if (result.emailSent()) ra.addFlashAttribute("success", "Đã gửi lại OTP tới email " + user.getEmail() + ".");
            else if (result.demoCode() != null) {
                ra.addFlashAttribute("info", "Chế độ demo chưa cấu hình SMTP; mã OTP thử nghiệm mới được hiển thị bên dưới.");
                ra.addFlashAttribute("demoOtp", result.demoCode());
            } else {
                ra.addFlashAttribute("error", "Không gửi được OTP qua email. Hãy kiểm tra cấu hình SMTP trước khi quyết toán.");
            }
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/orders/" + id + "/progress";
    }

    @PostMapping("/orders/{id}/verify-otp")
    String verifyOtp(@PathVariable Long id, @RequestParam String otp, Authentication auth, RedirectAttributes ra) {
        var user = current.require(auth);
        try {
            escrow.verifyOtpAndSettle(id, user, otp);
            ra.addFlashAttribute("success", "OTP hợp lệ. Khoản ký quỹ đã được quyết toán sang ví Provider.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/orders/" + id + "/progress";
    }

    @PostMapping("/orders/{id}/cancellation-compensation/resolve")
    String resolveCompensation(@PathVariable Long id, @RequestParam boolean accept, Authentication auth, RedirectAttributes ra) {
        var user = current.require(auth);
        try {
            escrow.customerResolveCompensation(id, user, accept);
            ra.addFlashAttribute("success", accept
                    ? "Đã xác nhận chi phí phần việc. Provider nhận khoản đã thống nhất và phần ký quỹ còn lại được hoàn về ví."
                    : "Bạn chưa chấp nhận chi phí. Đơn trở lại trạng thái đang thực hiện để hai bên tiếp tục trao đổi.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/orders/" + id + "/progress";
    }

    @GetMapping("/customer/wallet")
    String wallet(Authentication auth, Model model) {
        var user = current.require(auth);
        if (user.getRole() != Role.CUSTOMER) return "redirect:/workspace";
        var customerWallet = escrow.wallet(user);
        model.addAttribute("wallet", customerWallet);
        model.addAttribute("walletOriginalTotal", customerWallet.getBalance().add(customerWallet.getLockedAmount()));
        model.addAttribute("transactions", escrow.transactions(user));
        model.addAttribute("demoTopupEnabled", escrow.isDemoTopupEnabled());
        model.addAttribute("vnpayConfigured", walletTopUp.configured());
        return "customer/wallet";
    }

    @PostMapping("/customer/wallet/topup-vnpay")
    String topUpViaVnPay(@RequestParam BigDecimal amount, Authentication auth, HttpServletRequest request, RedirectAttributes ra) {
        var user = current.require(auth);
        if (user.getRole() != Role.CUSTOMER) return "redirect:/workspace";
        try {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
            if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
            return "redirect:" + walletTopUp.createPayment(user, amount, ip);
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/customer/wallet";
        }
    }

    @GetMapping("/customer/wallet/vnpay/return")
    String walletVnPayReturn(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        try {
            boolean paid = walletTopUp.processReturn(params);
            if (paid) ra.addFlashAttribute("success", "VNPay đã xác nhận giao dịch Sandbox. Số dư ví được cập nhật.");
            else ra.addFlashAttribute("error", "Giao dịch nạp ví không thành công hoặc dữ liệu xác minh không hợp lệ. Ví không được cộng tiền.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "Không thể xác minh kết quả nạp ví. Ví chưa được cộng tiền.");
        }
        return "redirect:/customer/wallet";
    }

    @PostMapping("/customer/wallet/topup-demo")
    String topUpDemo(@RequestParam BigDecimal amount, Authentication auth, RedirectAttributes ra) {
        var user = current.require(auth);
        if (user.getRole() != Role.CUSTOMER) return "redirect:/workspace";
        try {
            escrow.topUpDemo(user, amount);
            ra.addFlashAttribute("success", "Đã cộng số dư thử nghiệm. Đây không phải khoản tiền nạp qua ngân hàng.");
        } catch (RuntimeException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/customer/wallet";
    }
}
