package com.skillrental.controller;

import com.skillrental.dto.DepositForm;
import com.skillrental.entity.Wallet;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller @RequiredArgsConstructor
public class WalletController {
    private final CurrentUserService current;
    private final WalletService wallets;

    @GetMapping("/wallet")
    String wallet(@RequestParam(required = false) BigDecimal required,
                  @RequestParam(required = false) String next,
                  Authentication authentication, Model model) {
        var user = current.require(authentication);
        Wallet wallet = wallets.getOrCreate(user);
        model.addAttribute("wallet", wallet);
        model.addAttribute("transactions", wallets.history(user));
        model.addAttribute("required", required);
        model.addAttribute("next", safeNext(next));
        if (!model.containsAttribute("depositForm")) {
            model.addAttribute("depositForm", new DepositForm());
        }
        return "wallet/index";
    }

    @PostMapping("/wallet/deposit")
    String deposit(@Valid @ModelAttribute("depositForm") DepositForm form,
                   BindingResult binding,
                   @RequestParam(required = false) String next,
                   Authentication authentication,
                   Model model, RedirectAttributes ra) {
        var user = current.require(authentication);
        if (binding.hasErrors()) {
            model.addAttribute("wallet", wallets.getOrCreate(user));
            model.addAttribute("transactions", wallets.history(user));
            model.addAttribute("next", safeNext(next));
            return "wallet/index";
        }
        wallets.deposit(user, form.getAmount(), form.getMethod());
        ra.addFlashAttribute("success",
                "Đã nạp " + form.getAmount().stripTrailingZeros().toPlainString() + "đ vào ví (demo).");
        String target = safeNext(next);
        if (target != null) {
            return "redirect:" + target;
        }
        return "redirect:/wallet";
    }

    private String safeNext(String next) {
        if (next == null || next.isBlank()) return null;
        String value = next.trim();
        if (!value.startsWith("/") || value.startsWith("//")) return null;
        return value;
    }
}
