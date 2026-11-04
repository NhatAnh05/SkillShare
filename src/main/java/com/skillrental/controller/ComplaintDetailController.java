package com.skillrental.controller;
import com.skillrental.service.*;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Controller;import org.springframework.web.bind.annotation.*;import org.springframework.security.core.Authentication;import org.springframework.ui.Model;
@Controller @RequiredArgsConstructor public class ComplaintDetailController {
 private final CurrentUserService current;private final ComplaintHandlingService complaints;
 @GetMapping("/complaints/{id}") public String detail(@PathVariable Long id,Authentication a,Model m){m.addAttribute("complaint",complaints.view(id,current.require(a)));return "interaction/complaint-detail";}
 @PostMapping("/admin/complaints/{id}/claim") public String claim(@PathVariable Long id,Authentication a){complaints.claim(id,current.require(a));return "redirect:/complaints/"+id;}
}
