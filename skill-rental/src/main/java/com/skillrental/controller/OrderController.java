package com.skillrental.controller;

import com.skillrental.dto.PaymentForm;
import com.skillrental.entity.OrderStatus;
import com.skillrental.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor public class OrderController {
    private final CurrentUserService current;
    private final OrderService orders;
    @GetMapping("/orders") String list(Authentication a,Model m) {
        var u=current.require(a);
        m.addAttribute("orders",orders.list(u));
        m.addAttribute("completedCount",orders.completed(u));
        m.addAttribute("processingCount",orders.processingCount(u));
        return "orders/orders";
    }
    @GetMapping("/payment/{id}") String payment(@PathVariable Long id,Authentication a,Model m) {
        m.addAttribute("order",orders.owned(id,current.require(a)));
        m.addAttribute("form",new PaymentForm());
        return "booking/payment";
    }
    @PostMapping("/payment/{id}") String pay(@PathVariable Long id,@Valid @ModelAttribute("form")PaymentForm f,BindingResult br,Authentication a,Model m,RedirectAttributes ra) {
        var o=orders.owned(id,current.require(a));
        if(br.hasErrors()) {
            m.addAttribute("order",o);
            return "booking/payment";
        }
        try {
            orders.pay(o,f.getMethod(),f.getCouponCode(),f.isAcceptedTerms());
            ra.addFlashAttribute("success","Thanh toán thành công.");
            return "redirect:/booking/complete/" + id;
        } catch(Exception e) {
            br.reject("pay",e.getMessage());
            m.addAttribute("order",o);
            return "booking/payment";
        }
    }
    @GetMapping("/booking/complete/{id}")
    String bookingComplete(@PathVariable Long id, Authentication authentication, Model model) {
        var order = orders.owned(id, current.require(authentication));
        model.addAttribute("order", order);
        return "booking/complete";
    }

    @GetMapping("/orders/table") String table(Authentication auth,Model model) {
        var u=current.require(auth);
        model.addAttribute("orders",orders.list(u));
        return "orders/orders :: orderTable";
    }
    @GetMapping("/orders/{id}/details") String details(@PathVariable Long id,Authentication auth,Model model) {
        model.addAttribute("order",orders.owned(id,current.require(auth)));
        return "orders/progress :: details";
    }
    @PostMapping("/orders/{id}/state") String state(@PathVariable Long id,@RequestParam String action,Authentication auth,Model model) {
        var order=orders.owned(id,current.require(auth));
        if("complete".equals(action)&&order.getStatus()==OrderStatus.PROCESSING) {
            orders.complete(order);
        }
        else if("processing".equals(action)&&order.getStatus()==OrderStatus.PAID) {
            orders.processing(order);
        }
        else throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Trạng thái không cho phép");
        model.addAttribute("order",orders.owned(id,current.require(auth)));
        return "orders/progress :: details";
    }
    @GetMapping("/orders/{id}/progress") String progress(@PathVariable Long id,Authentication a,Model m) {
        m.addAttribute("order",orders.owned(id,current.require(a)));
        return "orders/progress";
    }
    @PostMapping("/orders/{id}/processing") String processing(@PathVariable Long id,Authentication a) {
        orders.processing(orders.owned(id,current.require(a)));
        return "redirect:/orders/"+id+"/progress";
    }
    @PostMapping("/orders/{id}/complete") String complete(@PathVariable Long id,Authentication a,RedirectAttributes ra) {
        orders.complete(orders.owned(id,current.require(a)));
        ra.addFlashAttribute("success","Đã xác nhận hoàn thành.");
        return "redirect:/orders";
    }
}
