package com.skillrental.controller;

import com.skillrental.dto.*;
import com.skillrental.repository.ReviewRepository;
import com.skillrental.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller @RequiredArgsConstructor public class InteractionController {
    private final CurrentUserService current;
    private final ChatService chat;
    private final OrderService orders;
    private final ReviewService reviews;
    private final ReviewRepository reviewRepo;
    @PostMapping("/chat/{id}/send") String send(@PathVariable Long id,@Valid @ModelAttribute("messageForm")MessageForm f,BindingResult br,Authentication a) {
        var u=current.require(a);
        if(!br.hasErrors())chat.send(chat.owned(id,u),u,f.getContent());
        return "redirect:/chat?conversationId="+id;
    }
    @GetMapping("/chat/{id}/messages") @ResponseBody List<Map<String,Object>> poll(@PathVariable Long id,Authentication a) {
        var u=current.require(a);
        var fmt=DateTimeFormatter.ofPattern("HH:mm");
        return chat.messages(chat.owned(id,u)).stream().map(x->Map.<String,Object>of("id",x.getId(),"own",x.getSender().getId().equals(u.getId()),"content",x.getContent(),"time",x.getSentAt().format(fmt))).toList();
    }
    @GetMapping("/orders/{id}/review") String review(@PathVariable Long id,Authentication a,Model m) {
        var o=orders.owned(id,current.require(a));
        if (o.getStatus() != com.skillrental.entity.OrderStatus.COMPLETED) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Chỉ có thể đánh giá đơn hàng đã hoàn thành.");
        }
        var f=new ReviewForm();
        reviewRepo.findByOrder(o).ifPresent(r-> {
            f.setRating(r.getRating());f.setComment(r.getComment());
        }
        );
        m.addAttribute("order",o);
        m.addAttribute("form",f);
        return "interaction/review";
    }
    @PostMapping("/orders/{id}/review") String save(@PathVariable Long id,@Valid @ModelAttribute("form")ReviewForm f,BindingResult br,Authentication a,Model m,RedirectAttributes ra) {
        var u=current.require(a);
        var o=orders.owned(id,u);
        if (o.getStatus() != com.skillrental.entity.OrderStatus.COMPLETED) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Chỉ có thể đánh giá đơn hàng đã hoàn thành.");
        }
        if(br.hasErrors()) {
            m.addAttribute("order",o);
            return "interaction/review";
        }
        try {
            reviews.save(o,u,f);
            ra.addFlashAttribute("success","Đã gửi đánh giá.");
            return "redirect:/orders";
        } catch(Exception e) {
            br.reject("review",e.getMessage());
            m.addAttribute("order",o);
            return "interaction/review";
        }
    }
}
