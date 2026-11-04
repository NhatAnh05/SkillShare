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
        if(!br.hasErrors()) {
            try {
                chat.send(chat.requireParticipant(id,u),u,f.getContent());
            } catch(IllegalArgumentException ex) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,ex.getMessage(),ex);
            }
        }
        return "redirect:/chat?conversationId="+id;
    }
    @GetMapping("/chat/{id}/messages") @ResponseBody List<Map<String,Object>> poll(@PathVariable Long id,@RequestParam(required=false) Long beforeId,Authentication a) {
        var u=current.require(a);
        try {
            var conversation=chat.requireParticipant(id,u);

            return chat.page(conversation,beforeId).stream().map(x->{
                Map<String,Object> item=new LinkedHashMap<>();
                item.put("id",x.getId());
                item.put("own",x.getSender().getId().equals(u.getId()));
                item.put("sender",x.getSender().getFullName());
                item.put("content",x.getContent());
                item.put("time",x.getSentAt().format(DateTimeFormatter.ofPattern("HH:mm")));
                item.put("read",x.getReadAt()!=null);
                return item;
            }).toList();
        } catch(IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,ex.getMessage(),ex);
        }
    }
    @PostMapping("/chat/{id}/read") @ResponseBody Map<String,Object> read(@PathVariable Long id,Authentication auth){
        var u=current.require(auth);try{chat.markRead(chat.requireParticipant(id,u),u);return Map.of("success",true);}catch(IllegalArgumentException ex){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);}
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
