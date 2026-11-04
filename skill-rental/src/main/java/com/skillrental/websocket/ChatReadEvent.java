package com.skillrental.websocket;

public record ChatReadEvent(Long conversationId, Long readerId) {}
