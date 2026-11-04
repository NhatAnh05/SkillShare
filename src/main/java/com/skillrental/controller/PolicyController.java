package com.skillrental.controller;

import com.skillrental.entity.TermsAcceptance;
import com.skillrental.repository.TermsAcceptanceRepository;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.PolicyVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class PolicyController {
    private final TermsAcceptanceRepository acceptances;
    private final CurrentUserService current;

    @GetMapping("/policies/{slug}")
    public String page(@PathVariable String slug, Model model) {
        com.skillrental.service.PolicyContent.Page page = com.skillrental.service.PolicyContent.PAGES.get(slug);
        if (page == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND);
        model.addAttribute("pageTitle", page.title());
        model.addAttribute("paragraphs", page.paragraphs());
        model.addAttribute("policyVersion", PolicyVersion.CURRENT);
        return "policies/page";
    }

    @GetMapping("/terms/accepted")
    public String accepted(Authentication authentication, Model model) {
        var user = current.require(authentication);
        List<TermsAcceptance> accepted = acceptances.findByUserOrderByAcceptedAtDesc(user);
        model.addAttribute("acceptedPolicies", accepted);
        return "policies/accepted";
    }


}
