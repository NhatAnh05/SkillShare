package com.skillrental.controller;

import com.skillrental.config.GoogleOAuthConfiguration.GoogleLoginSettings;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.skillrental.service.AuthService.OtpVerificationResult;
import com.skillrental.service.AuthService.OtpResendResult;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Controller @RequiredArgsConstructor public class AuthController {
    private static final String PENDING_REGISTRATION_EMAIL = "pendingRegistrationEmail";
    private final AuthService auth;
    private final CurrentUserService current;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final GoogleLoginSettings googleLoginSettings;
    @Value("${spring.mail.username:}") private String mailUsername;
    @Value("${app.base-url:http://localhost:8080}") private String appBaseUrl;
    @GetMapping("/login") String login(Model model) {
        model.addAttribute("googleLoginEnabled", googleLoginSettings.enabled());
        return "auth/login";
    }
    @GetMapping("/register") String register(Model model, HttpSession session) {
        RegisterRequest form = new RegisterRequest();
        String email = (String) session.getAttribute(PENDING_REGISTRATION_EMAIL);
        AuthService.OtpPageState state = auth.getRegistrationOtpPageState(email);
        if (state.eligible()) {
            form.setEmail(email);
            form.setFullName(state.fullName());
        } else if (email != null) {
            session.removeAttribute(PENDING_REGISTRATION_EMAIL);
            state = auth.getRegistrationOtpPageState(null);
        }
        addRegistrationModel(model, form, state, email, null);
        return "auth/register";
    }

    @PostMapping("/register/send-otp")
    @ResponseBody
    ResponseEntity<OtpSendResponse> sendRegistrationOtp(
            @Valid @ModelAttribute("form") RegisterRequest form, BindingResult bindingResult,
            HttpSession session) {
        if (bindingResult.hasErrors()) {
            String message = bindingResult.getAllErrors().get(0).getDefaultMessage();
            return ResponseEntity.badRequest().body(new OtpSendResponse(false, message, null, null));
        }

        try {
            OtpResendResult result = auth.sendRegistrationOtp(form);
            String email = form.getEmail().strip().toLowerCase(java.util.Locale.ROOT);
            AuthService.OtpPageState state = auth.getRegistrationOtpPageState(email);
            long availableAt = state.resendAvailableAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long serverNow = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            if (result == OtpResendResult.TOO_SOON) {
                session.setAttribute(PENDING_REGISTRATION_EMAIL, email);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                        new OtpSendResponse(false, "Vui lòng chờ hết thời gian đếm ngược trước khi gửi lại.", availableAt, serverNow));
            }
            if (result != OtpResendResult.SENT) {
                return ResponseEntity.badRequest().body(
                        new OtpSendResponse(false, "Không tìm thấy tài khoản đang chờ xác minh cho email này.", null, serverNow));
            }
            session.setAttribute(PENDING_REGISTRATION_EMAIL, email);
            return ResponseEntity.ok(new OtpSendResponse(true,
                    "Máy chủ email đã chấp nhận thư. Thư có thể mất thời gian để đến; hãy kiểm tra cả Spam/Junk.",
                    availableAt, serverNow));
        } catch (AuthService.OtpEmailDeliveryException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new OtpSendResponse(false, ex.getMessage(), null, null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new OtpSendResponse(false, ex.getMessage(), null, null));
        }
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute("form") RegisterRequest form, BindingResult bindingResult,
                    @RequestParam(required = false) String otp, HttpSession session, Model model,
                    RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            AuthService.OtpPageState state = auth.getRegistrationOtpPageState(
                    (String) session.getAttribute(PENDING_REGISTRATION_EMAIL));
            addRegistrationModel(model, form, state,
                    (String) session.getAttribute(PENDING_REGISTRATION_EMAIL),
                    "Vui lòng kiểm tra lại thông tin đăng ký.");
            return "auth/register";
        }
        String pendingEmail = (String) session.getAttribute(PENDING_REGISTRATION_EMAIL);
        if (pendingEmail == null || pendingEmail.isBlank()) {
            addRegistrationModel(model, form, auth.getRegistrationOtpPageState(null), null,
                    "Vui lòng gửi mã OTP để xác minh email trước khi đăng ký.");
            return "auth/register";
        }
        if (form.getEmail() == null || !pendingEmail.equalsIgnoreCase(form.getEmail().strip())) {
            addRegistrationModel(model, form, auth.getRegistrationOtpPageState(pendingEmail), pendingEmail,
                    "Email đã thay đổi sau khi gửi mã. Hãy gửi mã OTP mới cho email hiện tại.");
            return "auth/register";
        }

        OtpVerificationResult result;
        try {
            result = auth.completeRegistration(form, pendingEmail, otp);
        } catch (IllegalArgumentException ex) {
            addRegistrationModel(model, form, auth.getRegistrationOtpPageState(pendingEmail), pendingEmail,
                    ex.getMessage());
            return "auth/register";
        }
        if (result == OtpVerificationResult.VERIFIED) {
            session.removeAttribute(PENDING_REGISTRATION_EMAIL);
            redirectAttributes.addFlashAttribute("success", "Đăng ký tài khoản thành công");
            return "redirect:/login";
        }
        String error = switch (result) {
            case EXPIRED -> "OTP đã hết hạn. Hãy yêu cầu gửi lại mã mới.";
            case TOO_MANY_ATTEMPTS -> "Bạn đã nhập sai OTP quá 5 lần. Hãy yêu cầu gửi mã mới.";
            default -> "OTP không đúng hoặc yêu cầu xác minh không hợp lệ.";
        };
        addRegistrationModel(model, form, auth.getRegistrationOtpPageState(pendingEmail), pendingEmail, error);
        return "auth/register";
    }

    @GetMapping("/verify-otp")
    String legacyVerifyOtp() {
        return "redirect:/register";
    }

    @PostMapping({"/verify-otp", "/resend-registration-otp", "/registration-otp/send"})
    String legacyOtpPost(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error",
                "Hãy gửi mã và hoàn tất xác minh ngay trên trang đăng ký.");
        return "redirect:/register";
    }

    private void addRegistrationModel(Model model, RegisterRequest form, AuthService.OtpPageState state,
                                      String sessionEmail, String otpError) {
        model.addAttribute("form", form);
        model.addAttribute("otpPageState", state);
        boolean otpSentForEmail = state.eligible() && state.previouslySent() && sessionEmail != null
                && form.getEmail() != null && sessionEmail.equalsIgnoreCase(form.getEmail().strip());
        model.addAttribute("otpSentForEmail", otpSentForEmail);
        model.addAttribute("pendingEmail", sessionEmail == null ? "" : sessionEmail);
        model.addAttribute("resendAvailableAtEpochMillis",
                state.resendAvailableAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        model.addAttribute("serverNowEpochMillis",
                LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        model.addAttribute("otpError", otpError);
    }

    public record OtpSendResponse(boolean success, String message, Long resendAvailableAtEpochMillis,
                                  Long serverNowEpochMillis) {}

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
