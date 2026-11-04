package com.skillrental.controller;

import com.skillrental.dto.ComplaintForm;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.OrderService;
import com.skillrental.service.ReviewService;
import com.skillrental.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor
public class ComplaintController {
    private final CurrentUserService current;
    private final OrderService orders;
    private final ReviewService reviews;
    private final NotificationService notifications;
    @GetMapping("/orders/{id}/complaint")
    public String form(@PathVariable Long id,Authentication auth,Model model) {
        var user = current.require(auth);
        var order = orders.involved(id, user);
        model.addAttribute("order", order);
        model.addAttribute("reporter", user);
        model.addAttribute("reportedUser", order.getCustomer().getId().equals(user.getId()) ? order.getProvider() : order.getCustomer());
        model.addAttribute("complaintForm",new ComplaintForm());
        return "interaction/complaint";
    }
    @PostMapping("/orders/{id}/complaint")
    public String submit(@PathVariable Long id,@Valid @ModelAttribute("complaintForm")ComplaintForm form,
    BindingResult errors,Authentication auth,Model model,RedirectAttributes redirect) {
        var user=current.require(auth);
        var order=orders.involved(id,user);
        if(errors.hasErrors()) {
            model.addAttribute("order",order);
            model.addAttribute("reporter", user);
            model.addAttribute("reportedUser", order.getCustomer().getId().equals(user.getId()) ? order.getProvider() : order.getCustomer());
            return "interaction/complaint";
        }
        var complaint=reviews.complain(order,user,form);
        notifications.notifyAdmins("COMPLAINT", "Khiếu nại mới",
                "Có khiếu nại mới liên quan đến đơn hàng #" + id + ".",
                "COMPLAINT", complaint.getId(), "/complaints/"+complaint.getId());
        redirect.addFlashAttribute("success", "Báo cáo đã được ghi nhận và chuyển đến Admin xem xét.");
        return user.getRole() == com.skillrental.entity.Role.PROVIDER
                ? "redirect:/provider/bookings" : "redirect:/orders";
    }
}
