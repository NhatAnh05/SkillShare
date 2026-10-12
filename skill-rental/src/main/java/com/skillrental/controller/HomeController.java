package com.skillrental.controller;

import com.skillrental.service.MarketplaceService;
import com.skillrental.service.RequestService;
import com.skillrental.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {
    private final MarketplaceService marketplace;
    private final SkillRepository skills;
    private final RequestService requests;

    @GetMapping("/")
    String root() {
        return "redirect:/marketplace";
    }

    @GetMapping("/marketplace")
    String home(Model model) {
        model.addAttribute("providers", marketplace.search(null, null, null, "rating"));
        model.addAttribute("skills", skills.findAll());

        var recentRequests = requests.recentOpenRequests(6);
        model.addAttribute("recentRequests", recentRequests);
        model.addAttribute("requestApplyCounts", requests.applicationCounts(recentRequests));
        return "home";
    }
}
