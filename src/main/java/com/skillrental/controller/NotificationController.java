package com.skillrental.controller;

import com.skillrental.entity.Notification;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class NotificationController {
    private final CurrentUserService current;
    private final NotificationService notifications;

    @GetMapping("/notifications")
    @ResponseBody
    public Map<String, Object> list(Authentication authentication) {
        var recipient = current.require(authentication);
        List<Map<String,Object>> items = notifications.latest(recipient).stream()
                .map(this::dto).toList();
        return Map.of("items", items, "unreadCount", notifications.unreadCount(recipient));
    }

    @GetMapping("/notifications/all")
    public String all(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        var recipient = current.require(authentication);
        var inbox = notifications.inbox(recipient, page);
        model.addAttribute("notifications", inbox.getContent());
        model.addAttribute("notificationPage", inbox);
        model.addAttribute("unreadCount", notifications.unreadCount(recipient));
        return "notifications/all";
    }

    @GetMapping("/notifications/item/{id}")
    public String detail(@PathVariable Long id, Authentication authentication, Model model) {
        var recipient = current.require(authentication);
        if (!notifications.markRead(id, recipient)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo.");
        }
        model.addAttribute("notification", notifications.findForRecipient(id, recipient)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "notifications/detail";
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseBody
    public Map<String, Object> markRead(@PathVariable Long id, Authentication authentication) {
        var recipient = current.require(authentication);
        if (!notifications.markRead(id, recipient)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo.");
        }
        return Map.of("unreadCount", notifications.unreadCount(recipient));
    }

    @PostMapping("/notifications/read-all")
    @ResponseBody
    public Map<String, Object> markAllRead(Authentication authentication) {
        var recipient = current.require(authentication);
        notifications.markAllRead(recipient);
        return Map.of("unreadCount", notifications.unreadCount(recipient));
    }

    private Map<String,Object> dto(Notification item) {
        return Map.of("id", item.getId(), "notificationType", item.getType(),
                "title", item.getTitle(), "content", item.getContent(),
                "sender", item.getSender() == null ? "" : item.getSender().getFullName(),
                "targetType", item.getTargetType() == null ? "" : item.getTargetType(),
                "targetId", item.getTargetId() == null ? 0L : item.getTargetId(),
                "url", item.getTargetUrl(),
                "createdAt", item.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "read", item.getReadAt() != null);
    }
}
