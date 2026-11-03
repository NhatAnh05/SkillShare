package com.skillrental.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NotificationAudienceRequest(
        @NotBlank @Size(max = 32) String audience,
        @Size(max = 500) List<Long> recipientIds) {
}
