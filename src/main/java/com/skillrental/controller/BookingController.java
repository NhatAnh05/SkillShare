package com.skillrental.controller;

import com.skillrental.dto.BookingForm;
import com.skillrental.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import com.skillrental.entity.Role;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor public class BookingController {
    private final MarketplaceService marketplace;
    private final CurrentUserService current;
    private final BookingService bookings;
    @GetMapping("/booking/provider/{id}") String form(@PathVariable Long id,@RequestParam(defaultValue="false")boolean premium,Authentication authentication,Model m) {
        var user = current.require(authentication);
        if (user.getRole() != Role.CUSTOMER) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Chỉ Customer mới được đặt Provider.");
        var f=new BookingForm();
        f.setPremium(premium);
        f.setDirectBooking(true);
        m.addAttribute("form",f);
        m.addAttribute("provider",marketplace.get(id));
        return "booking/booking";
    }
    @PostMapping("/booking/provider/{id}") String submit(@PathVariable Long id,@Valid @ModelAttribute("form")BookingForm f,BindingResult br,Authentication a,Model m,RedirectAttributes ra) {
        if(br.hasErrors()) {
            m.addAttribute("provider",marketplace.get(id));
            return "booking/booking";
        }
        if (f.getBookingDate().equals(java.time.LocalDate.now()) &&
            f.getTimeSlot().isBefore(java.time.LocalTime.now())) {
            br.rejectValue("timeSlot", "past", "Khung giờ đã qua");
            m.addAttribute("provider", marketplace.get(id));
            return "booking/booking";
        }
        if (!java.util.Set.of(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(12, 0),
                              java.time.LocalTime.of(18, 0), java.time.LocalTime.of(20, 0))
                .contains(f.getTimeSlot())) {
            br.rejectValue("timeSlot", "invalid", "Khung giờ không hợp lệ");
            m.addAttribute("provider", marketplace.get(id));
            return "booking/booking";
        }
        try {
            var user = current.require(a);
            if (user.getRole() != com.skillrental.entity.Role.CUSTOMER) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Chỉ Customer mới được đặt Provider.");
            }
            var o=bookings.create(user,id,f);
            ra.addFlashAttribute("success", "Đã gửi yêu cầu đặt dịch vụ. Toàn bộ chi phí đã được giữ trong ví Escrow; Provider cần xác nhận nhận việc.");
            return "redirect:/orders/"+o.getId()+"/progress";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/customer/wallet";
        }
    }
}
