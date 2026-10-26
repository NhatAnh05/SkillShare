package com.skillrental.controller;

import com.skillrental.entity.OrderStatus;
import com.skillrental.entity.ProviderProfile;
import com.skillrental.entity.RentalOrder;
import com.skillrental.repository.ProviderProfileRepository;
import com.skillrental.repository.OfferedServiceRepository;
import com.skillrental.repository.RentalOrderItemRepository;
import com.skillrental.repository.CustomerProfileRepository;
import com.skillrental.service.CurrentUserService;
import com.skillrental.entity.User;
import org.springframework.security.core.Authentication;
import com.skillrental.entity.ServiceCatalog;
import com.skillrental.entity.ServiceStatus;
import com.skillrental.repository.RentalOrderRepository;
import com.skillrental.repository.SkillRepository;
import com.skillrental.service.MarketplaceService;
import com.skillrental.service.ProviderEligibilityService;
import com.skillrental.service.ServiceCatalogService;
import com.skillrental.service.RequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class HomeController {
    private final MarketplaceService marketplace;
    private final SkillRepository skills;
    private final RequestService requests;
    private final RentalOrderRepository orders;
    private final ProviderProfileRepository providerProfiles;
    private final OfferedServiceRepository offeredServices;
    private final RentalOrderItemRepository orderItems;
    private final CustomerProfileRepository customerProfiles;
    private final CurrentUserService currentUser;
    private final ProviderEligibilityService providerEligibility;
    private final ServiceCatalogService serviceCatalog;

    @GetMapping("/")
    String root() {
        return "redirect:/marketplace";
    }

    @GetMapping("/marketplace")
    String home(Model model, Authentication authentication) {
        model.addAttribute("providers", marketplace.search(null, null, null, "rating"));
        model.addAttribute("skills", skills.findAll());
        User viewer = currentUser.require(authentication);
        String preferred = customerProfiles.findByUser(viewer).map(p -> p.getPreferredSkills()).orElse("");
        List<String> preferredSkills = java.util.Arrays.stream((preferred == null ? "" : preferred).split(","))
                .map(String::trim).filter(value -> !value.isBlank()).map(String::toLowerCase).toList();
        List<ProviderProfile> recommendedProviders = providerProfiles.findAll().stream()
                .filter(providerEligibility::isEligible)
                .sorted((left, right) -> {
                    int leftMatches = (int) left.getSkills().stream()
                            .filter(skill -> preferredSkills.contains(skill.getName().toLowerCase())).count();
                    int rightMatches = (int) right.getSkills().stream()
                            .filter(skill -> preferredSkills.contains(skill.getName().toLowerCase())).count();
                    int bySkillMatch = Integer.compare(rightMatches, leftMatches);
                    if (bySkillMatch != 0) return bySkillMatch;
                    double leftRating = left.getRating() == null ? 0.0 : left.getRating();
                    double rightRating = right.getRating() == null ? 0.0 : right.getRating();
                    return Double.compare(rightRating, leftRating);
                })
                .limit(4).toList();
        model.addAttribute("recommendedProviders", recommendedProviders);
        model.addAttribute("recommendationBasis", preferredSkills.isEmpty() ? "đánh giá của cộng đồng" : "kỹ năng ưu tiên trong hồ sơ của bạn");

        Map<Long, Long> orderCounts = orderItems.countOrdersPerService(OrderStatus.CANCELLED).stream()
                .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), row -> ((Number) row[1]).longValue()));
        Map<Long, ProviderProfile> profilesByUserId = providerProfiles.findAll().stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), profile -> profile, (first, ignored) -> first));
        List<PopularServiceRow> popularServices = serviceCatalog.availablePublishedServices().stream()
                .filter(service -> profilesByUserId.containsKey(service.getProvider().getId()))
                .map(service -> {
                    ProviderProfile profile = profilesByUserId.get(service.getProvider().getId());
                    return new PopularServiceRow(service.getName(), service.getProvider().getFullName(), profile.getId(),
                            orderCounts.getOrDefault(service.getId(), 0L),
                            service.getRating() == null ? 0.0 : service.getRating(),
                            profile.getHourlyRate() == null ? BigDecimal.ZERO : profile.getHourlyRate());
                })
                .sorted(Comparator.comparingLong(PopularServiceRow::orderCount).reversed()
                        .thenComparing(Comparator.comparingDouble(PopularServiceRow::rating).reversed())
                        .thenComparing(PopularServiceRow::serviceName, String.CASE_INSENSITIVE_ORDER))
                .limit(6).toList();
        model.addAttribute("popularServices", popularServices);

        var recentRequests = requests.recentOpenRequests(6);
        model.addAttribute("recentRequests", recentRequests);
        model.addAttribute("requestApplyCounts", requests.applicationCounts(recentRequests));
        return "home";
    }

    private record PopularKey(Long providerId, String serviceName) {}
    public record PopularServiceRow(String serviceName, String providerName, Long providerProfileId,
                                    long orderCount, double rating, BigDecimal hourlyRate) {}
}
