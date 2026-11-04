package com.skillrental.controller;

import com.skillrental.service.MarketplaceService;
import com.skillrental.repository.SkillRepository;
import com.skillrental.repository.ReviewRepository;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@Controller @RequiredArgsConstructor
public class MarketplaceController {
    private final MarketplaceService marketplace;
    private final SkillRepository skills;
    private final ReviewRepository reviewRepository;
    @GetMapping("/providers")
    String search(@RequestParam(required=false)String q,@RequestParam(required=false)String skill,
    @RequestParam(required=false)BigDecimal minPrice,@RequestParam(required=false)BigDecimal maxPrice,
    @RequestParam(defaultValue="rating")String sort,Model model) {
        model.addAttribute("providers",marketplace.search(q,skill,minPrice,maxPrice,sort));
        model.addAttribute("skills",skills.findAll().stream().sorted((a,b)->a.getName().compareToIgnoreCase(b.getName())).toList());
        model.addAttribute("q",q);
        model.addAttribute("skill",skill);
        model.addAttribute("minPrice",minPrice==null?BigDecimal.ZERO:minPrice);
        model.addAttribute("maxPrice",maxPrice==null?new BigDecimal("2000000"):maxPrice);
        model.addAttribute("sort",sort);
        return "marketplace/search";
    }
    @GetMapping("/providers/{id}")String detail(@PathVariable Long id,Model model) {
        var provider = marketplace.get(id);
        var reviews = reviewRepository.findByProviderOrderByIdDesc(provider.getUser());
        int[] counts = new int[5];
        reviews.forEach(review -> counts[review.getRating() - 1]++);
        long total = reviews.size();
        int[] percents = IntStream.range(0, 5)
                .map(i -> total == 0 ? 0 : (int) Math.round(counts[i] * 100.0 / total)).toArray();
        model.addAttribute("provider", provider);
        model.addAttribute("reviews", reviews.stream().limit(4).toList());
        model.addAttribute("ratingCounts", counts);
        model.addAttribute("ratingPercent", percents);
        model.addAttribute("portfolioNames", List.of("Dự án thương mại điện tử", "Ứng dụng thanh toán", "Nền tảng quản lý", "Tối ưu hệ thống"));
        return "marketplace/provider-detail";
    }
}
