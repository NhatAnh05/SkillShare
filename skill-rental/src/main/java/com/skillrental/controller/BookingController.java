package com.skillrental.controller;

import com.skillrental.dto.BookingForm;
import com.skillrental.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor public class BookingController {
    private final MarketplaceService marketplace;
    private final CurrentUserService current;
    private final BookingService bookings;
    private final WalletService wallet;

    @GetMapping("/booking/provider/{id}") String form(@PathVariable Long id,@RequestParam(defaultValue="false")boolean premium,Authentication authentication,Model m) {
        var f=new BookingForm();
        f.setPremium(premium);
        f.setDirectBooking(true);
        m.addAttribute("form",f);
        m.addAttribute("provider",marketplace.get(id));
        m.addAttribute("walletBalance", wallet.available(current.require(authentication)));
        return "booking/booking";
    }
    @PostMapping("/booking/provider/{id}") String submit(@PathVariable Long id,@Valid @ModelAttribute("form")BookingForm f,BindingResult br,Authentication a,Model m,RedirectAttributes ra) {
        var user=current.require(a);
        if(br.hasErrors()) {
            m.addAttribute("provider",marketplace.get(id));
            m.addAttribute("walletBalance", wallet.available(user));
            return "booking/booking";
        }
        if (f.getBookingDate().equals(java.time.LocalDate.now()) &&
            f.getTimeSlot().isBefore(java.time.LocalTime.now())) {
            br.rejectValue("timeSlot", "past", "Khung giờ đã qua");
            m.addAttribute("provider", marketplace.get(id));
            m.addAttribute("walletBalance", wallet.available(user));
            return "booking/booking";
        }
        if (!java.util.Set.of(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(12, 0),
                              java.time.LocalTime.of(18, 0), java.time.LocalTime.of(20, 0))
                .contains(f.getTimeSlot())) {
            br.rejectValue("timeSlot", "invalid", "Khung giờ không hợp lệ");
            m.addAttribute("provider", marketplace.get(id));
            m.addAttribute("walletBalance", wallet.available(user));
            return "booking/booking";
        }
        try {
            var o=bookings.create(user,id,f);
            ra.addFlashAttribute("success","Đã đặt lịch và khóa tiền trong ví. Chưa thanh toán ngay.");
            return "redirect:/booking/complete/"+o.getId();
        } catch (InsufficientFundsException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/wallet?required=" + ex.getRequired()
                    + "&next=/booking/provider/" + id;
        }
    }
}
