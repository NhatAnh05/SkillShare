package com.skillrental.notification;

public record NotificationCreatedEvent(Long notificationId, Long recipientId) {}
