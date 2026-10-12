package com.skillrental.controller;

import com.skillrental.dto.ComplaintForm;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.OrderService;
import com.skillrental.service.ReviewService;
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
    @GetMapping("/orders/{id}/complaint")
    public String form(@PathVariable Long id,Authentication auth,Model model) {
        model.addAttribute("order",orders.owned(id,current.require(auth)));
        model.addAttribute("complaintForm",new ComplaintForm());
        return "interaction/complaint";
    }
    @PostMapping("/orders/{id}/complaint")
    public String submit(@PathVariable Long id,@Valid @ModelAttribute("complaintForm")ComplaintForm form,
    BindingResult errors,Authentication auth,Model model,RedirectAttributes redirect) {
        var user=current.require(auth);
        var order=orders.owned(id,user);
        if(errors.hasErrors()) {
            model.addAttribute("order",order);
            return "interaction/complaint";
        }
        reviews.complain(order,user,form);
        redirect.addFlashAttribute("success","Khiếu nại đã được ghi nhận.");
        return "redirect:/orders";
    }
}
