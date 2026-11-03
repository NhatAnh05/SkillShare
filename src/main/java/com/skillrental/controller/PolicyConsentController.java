package com.skillrental.controller;
import com.skillrental.service.*;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Controller;import org.springframework.web.bind.annotation.*;import org.springframework.security.core.Authentication;import org.springframework.ui.Model;
@Controller @RequiredArgsConstructor
public class PolicyConsentController {
 private final CurrentUserService current;private final PolicyConsentService consent;
 @GetMapping("/provider/terms") public String page(){return "policies/provider-consent";}
 @PostMapping("/provider/terms") public String accept(Authentication a,@RequestParam(defaultValue="false") boolean acceptedPolicy){consent.accept(current.require(a),"PROVIDER_POLICY","PROVIDER_ONBOARDING",acceptedPolicy);return "redirect:/provider/dashboard";}
 @GetMapping("/terms/accepted/{id}") public String archived(@PathVariable Long id,Authentication a,Model m){var snapshot=consent.accepted(id,current.require(a));m.addAttribute("pageTitle",snapshot.getTitle());m.addAttribute("paragraphs",java.util.List.of(snapshot.getContent()));m.addAttribute("policyVersion",snapshot.getPolicyVersion());return "policies/page";}
}
