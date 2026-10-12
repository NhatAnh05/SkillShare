package com.skillrental.controller;

import com.skillrental.dto.*;
import com.skillrental.repository.UserRepository;
import com.skillrental.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor public class AuthController {
    private final AuthService auth;
    private final CurrentUserService current;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    @GetMapping("/login") String login() {
        return "auth/login";
    }
    @GetMapping("/register") String register(Model m) {
        m.addAttribute("form",new RegisterRequest());
        return "auth/register";
    }
    @PostMapping("/register") String register(@Valid @ModelAttribute("form") RegisterRequest f,BindingResult br,RedirectAttributes ra) {
        if(br.hasErrors())return "auth/register";
        try {
            auth.register(f);
            ra.addFlashAttribute("success","Đăng ký thành công.");
            return "redirect:/login";
        } catch(Exception e) {
            br.reject("register",e.getMessage());
            return "auth/register";
        }
    }
    @GetMapping("/forgot-password") String forgot(@RequestParam(required=false)String token,Model m) {
        m.addAttribute("token",token);
        return "auth/forgot-password";
    }
    @PostMapping("/forgot-password") String forgotSubmit(@RequestParam String email,RedirectAttributes ra) {
        try {
            String t=auth.createResetToken(email);
            ra.addFlashAttribute("success","Demo: token reset đã tạo.");
            return "redirect:/forgot-password?token="+t;
        } catch(Exception e) {
            ra.addFlashAttribute("error",e.getMessage());
            return "redirect:/forgot-password";
        }
    }
    @PostMapping("/reset-password") String reset(@RequestParam String token,@RequestParam String newPassword,@RequestParam String confirmPassword,RedirectAttributes ra) {
        try {
            auth.reset(token,newPassword,confirmPassword);
            ra.addFlashAttribute("success","Đặt lại mật khẩu thành công.");
            return "redirect:/login";
        } catch(Exception e) {
            ra.addFlashAttribute("error",e.getMessage());
            return "redirect:/forgot-password?token="+token;
        }
    }
    @GetMapping("/change-password") String change(Model m) {
        m.addAttribute("form",new ChangePasswordForm());
        return "auth/change-password";
    }
    @PostMapping("/change-password") String change(@Valid @ModelAttribute("form")ChangePasswordForm f,BindingResult br,Authentication a,RedirectAttributes ra) {
        if(br.hasErrors())return "auth/change-password";
        var u=current.require(a);
        if(!encoder.matches(f.getCurrentPassword(),u.getPassword())) {
            br.reject("password","Mật khẩu hiện tại không đúng");
            return "auth/change-password";
        }
        if(!f.getNewPassword().equals(f.getConfirmPassword())) {
            br.reject("password","Mật khẩu xác nhận không khớp");
            return "auth/change-password";
        }
        u.setPassword(encoder.encode(f.getNewPassword()));
        users.save(u);
        ra.addFlashAttribute("success","Đổi mật khẩu thành công.");
        return "redirect:/profile";
    }
}
