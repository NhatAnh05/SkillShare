package com.skillrental.controller;

import com.skillrental.dto.*;
import com.skillrental.repository.UserRepository;
import com.skillrental.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${spring.mail.username:}") private String mailUsername;
    @Value("${app.base-url:http://localhost:8080}") private String appBaseUrl;
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
            var user = auth.register(f);
            ra.addFlashAttribute("success", "Tài khoản đã được tạo. Hãy mở email để xác nhận trước khi đăng nhập.");
            if ((mailUsername == null || mailUsername.isBlank()) && user.getEmailVerificationToken() != null) {
                ra.addFlashAttribute("verificationLink", appBaseUrl + "/verify-email?token=" + user.getEmailVerificationToken());
                ra.addFlashAttribute("verificationDemo", "SMTP chưa cấu hình: liên kết dưới đây chỉ dành cho kiểm thử local, không dùng trong production.");
            }
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
        String token = auth.createResetToken(email);
        ra.addFlashAttribute("success", "Nếu email tồn tại và tài khoản đang hoạt động, liên kết khôi phục sẽ được gửi đến hộp thư trong vài phút.");
        if (token != null && (mailUsername == null || mailUsername.isBlank()))
            ra.addFlashAttribute("resetDemoLink", appBaseUrl + "/forgot-password?token=" + token);
        return "redirect:/forgot-password";
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
    @GetMapping("/verify-email") String verifyEmail(@RequestParam String token, RedirectAttributes ra) {
        if (auth.verifyEmail(token)) ra.addFlashAttribute("success", "Email đã được xác nhận. Bạn có thể đăng nhập.");
        else ra.addFlashAttribute("error", "Liên kết xác nhận không hợp lệ hoặc đã hết hạn. Hãy đăng ký lại hoặc liên hệ quản trị viên.");
        return "redirect:/login";
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
