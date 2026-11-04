package com.skillrental.controller;

import com.skillrental.dto.AdminNotificationRequest;
import com.skillrental.dto.NotificationAudienceRequest;
import com.skillrental.entity.Role;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.NotificationCampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/notifications")
public class NotificationCampaignController {
    private final CurrentUserService current;
    private final NotificationCampaignService campaigns;

    @GetMapping("/recipients")
    @ResponseBody
    public List<Map<String, Object>> recipients(@RequestParam(defaultValue = "") String q,
                                                Authentication authentication) {
        requireAdmin(authentication);
        return campaigns.searchRecipients(q).stream()
                .map(user -> Map.<String, Object>of("id", user.getId(), "name", user.getFullName(),
                        "email", user.getEmail(), "role", user.getRole().name()))
                .toList();
    }

    @PostMapping("/preview")
    @ResponseBody
    public NotificationCampaignService.Preview preview(@Valid @RequestBody NotificationAudienceRequest request,
                                                        Authentication authentication) {
        requireAdmin(authentication);
        return campaigns.preview(request.audience(), request.recipientIds());
    }

    @PostMapping("/send")
    @ResponseBody
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> send(@Valid @RequestBody AdminNotificationRequest request,
                                    Authentication authentication) {
        var campaign = campaigns.send(requireAdmin(authentication), request);
        return Map.of("campaignId", campaign.getId(), "status", "QUEUED");
    }

    @GetMapping("/campaigns/{id}")
    @ResponseBody
    public NotificationCampaignService.CampaignDetail detail(@PathVariable Long id,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              Authentication authentication) {
        requireAdmin(authentication);
        return campaigns.detail(id, page);
    }

    @GetMapping("/campaigns/{id}/audit")
    @ResponseBody
    public List<NotificationCampaignService.AuditDetail> audit(@PathVariable Long id,
                                                              Authentication authentication) {
        requireAdmin(authentication);
        return campaigns.auditHistory(id);
    }

    @PostMapping("/campaigns/{id}/retry")
    @ResponseBody
    public Map<String, Object> retry(@PathVariable Long id, Authentication authentication) {
        campaigns.retryFailures(requireAdmin(authentication), id);
        return Map.of("success", true);
    }

    private com.skillrental.entity.User requireAdmin(Authentication authentication) {
        var user = current.require(authentication);
        campaigns.requireAdmin(user);
        return user;
    }
}
