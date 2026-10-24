package com.skillrental.controller;

import com.skillrental.entity.User;
import com.skillrental.repository.UserRepository;
import com.skillrental.repository.RentalOrderRepository;
import com.skillrental.repository.PaymentRepository;
import com.skillrental.entity.RentalOrder;
import com.skillrental.entity.Payment;
import com.skillrental.entity.OrderStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.skillrental.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class SettingsController {
    private final CurrentUserService currentUser;
    private final UserRepository users;
    private final RentalOrderRepository orders;
    private final PaymentRepository payments;

    @GetMapping("/settings")
    public String settings(Authentication authentication, Model model) {
        User user = currentUser.require(authentication);
        int count = 0;
        if (filled(user.getFullName())) count++;
        if (filled(user.getEmail())) count++;
        if (filled(user.getPhone())) count++;
        if (filled(user.getAvatarUrl())) count++;
        if (filled(user.getBio())) count++;
        model.addAttribute("completion", count * 20);
        return "customer/settings";
    }

    @GetMapping("/settings/coming-soon")
    public String comingSoon(@RequestParam(defaultValue = "Tính năng") String feature, Model model) {
        model.addAttribute("feature", feature);
        return "customer/coming-soon";
    }


    @GetMapping("/settings/billing")
    public String billing(Authentication authentication, Model model) {
        User user = currentUser.require(authentication);
        List<RentalOrder> ownedOrders = orders.findByCustomerOrderByIdDesc(user);
        List<Payment> transactions = new ArrayList<>();
        BigDecimal paidTotal = BigDecimal.ZERO;
        for (RentalOrder order : ownedOrders) {
            var payment = payments.findByOrder(order);
            if (payment.isPresent()) {
                transactions.add(payment.get());
                if (payment.get().getStatus() != null && payment.get().getStatus().name().equals("PAID")) {
                    paidTotal = paidTotal.add(payment.get().getAmount() == null ? BigDecimal.ZERO : payment.get().getAmount());
                }
            }
        }
        model.addAttribute("transactions", transactions);
        model.addAttribute("paidTotal", paidTotal);
        model.addAttribute("transactionCount", transactions.size());
        model.addAttribute("completedCount", orders.countByCustomerAndStatus(user, OrderStatus.COMPLETED));
        return "customer/billing";
    }

    @GetMapping("/settings/notifications")
    public String notificationSettings() { return "customer/notification-settings"; }

    @PostMapping("/settings/notifications")
    public String saveNotificationSettings(@RequestParam(defaultValue="false") boolean inApp,
                                           @RequestParam(defaultValue="false") boolean email,
                                           RedirectAttributes attributes) {
        attributes.addFlashAttribute("success", "Tùy chọn đã được ghi nhận cho phiên demo. Email notification preference không đồng nghĩa email đã được gửi.");
        return "redirect:/settings/notifications";
    }

    @GetMapping("/settings/kyc")
    public String kyc() { return "customer/kyc"; }

    @PostMapping("/settings/kyc")
    public String submitKyc(@RequestParam String legalName, @RequestParam String idNumber, RedirectAttributes attributes) {
        if (legalName == null || legalName.isBlank() || idNumber == null || idNumber.isBlank()) {
            attributes.addFlashAttribute("error", "Vui lòng nhập họ tên pháp lý và số giấy tờ.");
            return "redirect:/settings/kyc";
        }
        attributes.addFlashAttribute("success", "Hồ sơ demo đã được tiếp nhận trong phiên này; trạng thái minh họa là Chờ xét duyệt. Danh tính chưa được xác thực thật.");
        return "redirect:/settings/kyc";
    }

    @GetMapping("/settings/language")
    public String language() { return "customer/language"; }

    @PostMapping("/settings/language")
    public String saveLanguage(@RequestParam String language, jakarta.servlet.http.HttpServletRequest request, RedirectAttributes attributes) {
        if (!language.equals("vi") && !language.equals("en")) {
            attributes.addFlashAttribute("error", "Ngôn ngữ không hợp lệ.");
            return "redirect:/settings/language";
        }
        request.getSession(true).setAttribute("customerLanguagePreference", language);
        attributes.addFlashAttribute("success", "Đã lưu lựa chọn ngôn ngữ cho phiên hiện tại. Bản dịch toàn bộ Customer Portal chưa được triển khai.");
        return "redirect:/settings/language";
    }

    // TODO: nghiệp vụ thật sẽ bổ sung sau: đăng ký và xác thực OTP, recovery codes, audit log.
    // Đây chỉ là cờ tùy chọn giao diện; KHÔNG cho phép khẳng định 2FA đã hoạt động.
    @PostMapping("/settings/2fa-preference")
    public String twoFactorPreference(@RequestParam(defaultValue = "false") boolean enabled,
                                      Authentication authentication, RedirectAttributes attributes) {
        User user = currentUser.require(authentication);
        user.setTwoFactorPreference(enabled);
        users.save(user);
        attributes.addFlashAttribute("success", "Đã lưu tùy chọn demo; 2FA chưa bảo vệ đăng nhập.");
        return "redirect:/change-password";
    }

    private boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}
