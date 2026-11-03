package com.skillrental.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Only canonical, known read-only destinations may be used in Admin announcements. */
public final class NotificationLinks {
    private NotificationLinks() {}
    public static String validate(String input) {
        if (input == null || input.isBlank()) return null;
        String path = input.strip();
        boolean allowed = path.length() <= 500 && (
                path.matches("/policies/(terms|privacy|provider|booking|cancellation|messages|payments|cookies)")
                || path.matches("/(marketplace|providers|services|profile|requests|orders|settings|notifications/all)")
                || path.matches("/provider/(dashboard|orders|services)")
                || path.matches("/orders/[1-9][0-9]*/(progress|details)")
                || path.matches("/interaction/chat(\\?conversationId=[1-9][0-9]*)?"));
        if (!allowed) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Chọn đường dẫn nội bộ hợp lệ: /policies/terms, /marketplace, /orders hoặc /notifications/all.");
        return path;
    }
}
