package com.skillrental.notification;

public record NotificationReadEvent(Long recipientId, Long notificationId, long unreadCount) {}
