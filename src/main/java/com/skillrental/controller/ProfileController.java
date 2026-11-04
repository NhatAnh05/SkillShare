package com.skillrental.controller;

import com.skillrental.dto.ProfileForm;
import com.skillrental.repository.UserRepository;
import com.skillrental.service.CurrentUserService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor public class ProfileController {
    private final CurrentUserService current;
    private final UserRepository users;
    @GetMapping("/profile") String profile(Authentication a,Model m) {
        var u=current.require(a);
        var f=new ProfileForm();
        f.setFullName(u.getFullName());
        f.setEmail(u.getEmail());
        f.setPhone(u.getPhone());
        f.setAvatarUrl(u.getAvatarUrl());
        f.setBio(u.getBio());
        m.addAttribute("form",f);
        return "customer/profile";
    }
    @PostMapping("/profile") String update(@Valid @ModelAttribute("form")ProfileForm f,BindingResult br,Authentication a,HttpServletRequest req,HttpServletResponse res,RedirectAttributes ra) {
        if(br.hasErrors())return "customer/profile";
        var u=current.require(a);
        boolean changed=!u.getEmail().equalsIgnoreCase(f.getEmail());
        if(changed&&users.existsByEmailIgnoreCase(f.getEmail())) {
            br.rejectValue("email","duplicate","Email đã dùng");
            return "customer/profile";
        }
        u.setFullName(f.getFullName());
        u.setEmail(f.getEmail().toLowerCase());
        u.setPhone(f.getPhone());
        u.setAvatarUrl(f.getAvatarUrl());
        u.setBio(f.getBio());
        users.save(u);
        if(changed) {
            new SecurityContextLogoutHandler().logout(req,res,a);
            ra.addFlashAttribute("success","Đã đổi email. Đăng nhập lại.");
            return "redirect:/login";
        }
        ra.addFlashAttribute("success","Cập nhật hồ sơ thành công.");
        return "redirect:/profile";
    }
}
