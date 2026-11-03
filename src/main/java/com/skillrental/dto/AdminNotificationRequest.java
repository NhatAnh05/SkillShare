package com.skillrental.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminNotificationRequest(
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 1200) String content,
        @NotBlank @Size(max = 40) String type,
        @NotBlank @Size(max = 32) String audience,
        @Size(max = 500) List<Long> recipientIds,
        @Size(max = 500) String targetUrl,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String requestKey) {
}
