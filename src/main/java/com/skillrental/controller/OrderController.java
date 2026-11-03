package com.skillrental.controller;

import com.skillrental.entity.OrderStatus;
import com.skillrental.entity.Role;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.EscrowService;
import com.skillrental.service.OrderService;
import com.skillrental.service.VnPayWalletTopUpService;
import com.skillrental.service.DeliveryService;
import com.skillrental.entity.DeliverySubmission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    private final DeliveryService deliveries;
    private final com.skillrental.service.OrderWorkService work;

    private com.skillrental.entity.RentalOrder visibleOrder(Long id,com.skillrental.entity.User user){try{return orders.involved(id,user);}catch(IllegalArgumentException ex){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Bạn không tham gia đơn hàng này.");}}
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
        var user=current.require(auth);
        model.addAttribute("order", visibleOrder(id,user));
        model.addAttribute("deliveries",deliveries.history(id,user));
        model.addAttribute("deliveryRevisions",deliveries.revisionHistory(id,user));
        model.addAttribute("workUpdates", work.history(id,user));
        return "orders/progress :: details";
    }

    @GetMapping("/orders/{id}/progress")
    String progress(@PathVariable Long id, Authentication auth, Model model) {
        var user = current.require(auth);
        var order = visibleOrder(id,user);
        model.addAttribute("order", order);
        model.addAttribute("wallet", escrow.wallet(user));
        model.addAttribute("deliveries", deliveries.history(id, user));
        model.addAttribute("deliveryRevisions", deliveries.revisionHistory(id, user));
        model.addAttribute("workUpdates", work.history(id,user));
        return "orders/progress";
    }

    @PostMapping("/orders/{id}/work-updates")
    String updateWork(@PathVariable Long id, @RequestParam int percent, @RequestParam String description,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime estimatedCompletionAt,
            Authentication auth, RedirectAttributes ra) {
        try { work.update(id,current.require(auth),percent,description,estimatedCompletionAt);
            ra.addFlashAttribute("success","Đã cập nhật tiến độ và thông báo cho Customer.");
        } catch (RuntimeException ex) { ra.addFlashAttribute("error",ex.getMessage()); }
        return "redirect:/orders/"+id+"/progress";
    }

    @PostMapping("/orders/{id}/deliveries")
    String submitDelivery(@PathVariable Long id,
                          @RequestParam String description,
                          @RequestParam(required = false) String usageNotes,
                          @RequestParam(required = false, name = "links") List<String> links,
                          @RequestParam(required = false, name = "files") List<MultipartFile> files,
                          Authentication auth, RedirectAttributes ra) {
        try {
            deliveries.submit(id, current.require(auth), description, usageNotes, links, files);
            ra.addFlashAttribute("success", "Đã gửi sản phẩm bàn giao. Đơn đang chờ Customer nghiệm thu.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/orders/" + id + "/progress";
    }

    @PostMapping("/orders/{id}/deliveries/{deliveryId}/revision")
    String requestDeliveryRevision(@PathVariable Long id, @PathVariable Long deliveryId,
                                   @RequestParam String reason, Authentication auth, RedirectAttributes ra) {
        try {
            deliveries.requestRevision(id, current.require(auth), deliveryId, reason);
            ra.addFlashAttribute("success", "Đã gửi yêu cầu chỉnh sửa cho Provider.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/orders/" + id + "/progress";
    }

    @GetMapping("/orders/{id}/deliveries/{deliveryId}/files/{fileId}")
    ResponseEntity<Resource> downloadDeliveryFile(@PathVariable Long id, @PathVariable Long deliveryId,
                                                  @PathVariable Long fileId, Authentication auth) {
        visibleOrder(id,current.require(auth));
        var file = deliveries.download(id, deliveryId, fileId, current.require(auth));
        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            contentType = MediaType.parseMediaType(file.metadata().getContentType());
        } catch (IllegalArgumentException ignored) {
            // Invalid persisted MIME metadata must not prevent an authorized download.
        }
        return ResponseEntity.ok().contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.metadata().getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .body(file.resource());
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
            EscrowService.OtpDispatchResult result = deliveries.acceptLatest(id, user);
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
        model.addAttribute("recentTopUps", walletTopUp.recentTopUps(user));
        return "customer/wallet";
    }

    @PostMapping("/customer/wallet/topup-vnpay")
    String topUpViaVnPay(@RequestParam BigDecimal amount, Authentication auth, HttpServletRequest request, RedirectAttributes ra) {
        var user = current.require(auth);
        if (user.getRole() != Role.CUSTOMER) return "redirect:/workspace";
        try {
            // Do not trust arbitrary X-Forwarded-For values supplied by browsers.
            return "redirect:" + walletTopUp.createPayment(user, amount, request.getRemoteAddr());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/customer/wallet";
        }
    }

    /** Browser Return is read-only. Only the verified VNPay IPN can credit the wallet. */
    @GetMapping("/customer/wallet/vnpay/return")
    String walletVnPayReturn(@RequestParam Map<String, String> params, Authentication auth, RedirectAttributes ra) {
        if (auth == null || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
            return "redirect:/login";
        var user = current.require(auth);
        if (user.getRole() != Role.CUSTOMER) return "redirect:/workspace";
        switch (walletTopUp.statusForReturn(params, user)) {
            case "PAID" -> ra.addFlashAttribute("success", "VNPay đã xác nhận giao dịch qua IPN. Số dư ví đã cập nhật.");
            case "FAILED" -> ra.addFlashAttribute("error", "Giao dịch VNPay không thành công. Ví không được cộng tiền.");
            case "PENDING" -> ra.addFlashAttribute("info", "Đã quay về từ VNPay. Hệ thống đang chờ IPN xác minh; vui lòng tải lại trang ví để xem trạng thái.");
            default -> ra.addFlashAttribute("error", "Thông tin trả về từ VNPay không hợp lệ hoặc không thuộc tài khoản của bạn.");
        }
        return "redirect:/customer/wallet";
    }

    /** VNPay calls this server-to-server endpoint over public HTTPS in the Sandbox. */
    @GetMapping(value = "/customer/wallet/vnpay/ipn", produces = "application/json")
    @ResponseBody
    Map<String, String> walletVnPayIpn(@RequestParam Map<String, String> params) {
        try {
            return walletTopUp.processIpn(params).asMap();
        } catch (RuntimeException ex) {
            // Returning 99 asks VNPay to retry. The @Transactional service rolls back.
            return Map.of("RspCode", "99", "Message", "Internal error");
        }
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
