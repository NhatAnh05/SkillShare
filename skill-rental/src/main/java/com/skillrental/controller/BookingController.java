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

@Controller @RequiredArgsConstructor public class BookingController {
    private final MarketplaceService marketplace;
    private final CurrentUserService current;
    private final BookingService bookings;
    @GetMapping("/booking/provider/{id}") String form(@PathVariable Long id,@RequestParam(defaultValue="false")boolean premium,Model m) {
        var f=new BookingForm();
        f.setPremium(premium);
        f.setDirectBooking(true);
        m.addAttribute("form",f);
        m.addAttribute("provider",marketplace.get(id));
        return "booking/booking";
    }
    @PostMapping("/booking/provider/{id}") String submit(@PathVariable Long id,@Valid @ModelAttribute("form")BookingForm f,BindingResult br,Authentication a,Model m) {
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
        var o=bookings.create(current.require(a),id,f);
        return "redirect:/payment/"+o.getId();
    }
}
