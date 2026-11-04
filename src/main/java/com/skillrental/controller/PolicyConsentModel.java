package com.skillrental.controller;
import com.skillrental.service.*;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import org.springframework.security.core.Authentication;import org.springframework.ui.Model;
@ControllerAdvice @RequiredArgsConstructor
public class PolicyConsentModel {
 private final PolicyConsentService consent;private final CurrentUserService current;
 @ModelAttribute public void model(Authentication auth,Model model){if(auth==null||!auth.isAuthenticated()||auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)return;com.skillrental.entity.User user;try{user=current.require(auth);}catch(org.springframework.web.server.ResponseStatusException invalidSession){return;}model.addAttribute("bookingPolicyRequired",consent.needed(user,"BOOKING_POLICY"));model.addAttribute("providerPolicyRequired",consent.needed(user,"PROVIDER_POLICY"));}
}
