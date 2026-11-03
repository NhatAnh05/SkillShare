package com.skillrental.notification;

public record OrderStatusChangedEvent(Long orderId, String previousStatus, String newStatus, Long actorId) {}
