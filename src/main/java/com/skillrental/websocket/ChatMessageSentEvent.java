package com.skillrental.websocket;

import java.time.LocalDateTime;

public record ChatMessageSentEvent(Long conversationId, Long messageId, Long senderId, Long recipientId,
                                  String senderName, String content, LocalDateTime sentAt) {}
