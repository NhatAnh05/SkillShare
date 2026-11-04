package com.skillrental.websocket;
public record ChatMembershipChangedEvent(Long conversationId,java.util.List<Long> userIds){}
