package com.skillrental.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillrental.entity.Notification;
import com.skillrental.repository.NotificationRepository;
import com.skillrental.repository.UserRepository;
import com.skillrental.service.NotificationCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final NotificationCampaignService campaigns;
    private final ObjectMapper objectMapper;
    private final com.skillrental.websocket.SocketSessionRegistry sessionRegistry;
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if (session.getPrincipal() == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        Long userId = users.findByEmailIgnoreCase(session.getPrincipal().getName())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"))
                .getId();
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        session.getAttributes().put("notificationUserId", userId);
        sessionRegistry.register(session);
        try{sendSnapshot(session,userId);}catch(IOException|IllegalStateException disconnected){
            removeSession(userId,session);sessionRegistry.remove(session);
            try{session.close(CloseStatus.GOING_AWAY);}catch(IOException ignored){}
        }
    }

    @TransactionalEventListener
    public void onNotificationCreated(NotificationCreatedEvent event) {
        Notification notification = notifications.findById(event.notificationId()).orElse(null);
        if (notification == null) return;
        Set<WebSocketSession> sessions = sessionsByUser.get(event.recipientId());
        if (sessions == null || sessions.isEmpty()) {
            campaigns.recordWebSocketAttempt(event.notificationId(), 0, 0);
            return;
        }
        Map<String, Object> payload = payload(notification);
        payload.put("unreadCount", notifications.countByRecipientAndReadAtIsNull(notification.getRecipient()));
        int successful = 0;
        int failed = 0;
        for (WebSocketSession session : sessions) {
            try {
                if (send(session, payload)) successful++;
                else removeSession(event.recipientId(), session);
            } catch (IOException | IllegalStateException ex) {
                failed++;
                removeSession(event.recipientId(), session);
            }
        }
        campaigns.recordWebSocketAttempt(event.notificationId(), successful, failed);
    }

    @TransactionalEventListener
    public void onNotificationRead(NotificationReadEvent event) {
        Set<WebSocketSession> sessions = sessionsByUser.get(event.recipientId());
        if (sessions == null) return;
        Map<String, Object> payload = event.notificationId() == null
                ? Map.of("type", "read", "unreadCount", event.unreadCount())
                : Map.of("type", "read", "id", event.notificationId(), "unreadCount", event.unreadCount());
        sessions.forEach(session -> {
            try {
                send(session, payload);
            } catch (IOException | IllegalStateException ex) {
                removeSession(event.recipientId(), session);
            }
        });
    }

    private void sendSnapshot(WebSocketSession session, Long userId) throws IOException {
        var recipient = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        send(session, Map.of(
                "type", "snapshot",
                "items", notifications.findTop50ByRecipientOrderByCreatedAtDescIdDesc(recipient)
                        .stream().map(this::payload).toList(),
                "unreadCount", notifications.countByRecipientAndReadAtIsNull(recipient)));
    }

    private Map<String, Object> payload(Notification item) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("type", "notification");
        payload.put("id", item.getId());
        payload.put("notificationType", item.getType());
        payload.put("title", item.getTitle());
        payload.put("content", item.getContent());
        payload.put("sender", item.getSender() == null ? "" : item.getSender().getFullName());
        payload.put("targetType", item.getTargetType() == null ? "" : item.getTargetType());
        payload.put("targetId", item.getTargetId() == null ? 0L : item.getTargetId());
        payload.put("url", item.getTargetUrl());
        payload.put("createdAt", item.getCreatedAt().format(DATE_FORMAT));
        payload.put("read", item.getReadAt() != null);
        return payload;
    }

    private boolean send(WebSocketSession session, Object payload) throws IOException {
        synchronized (session) {
            if (!session.isOpen()) return false;
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            return true;
        }
    }

    private void removeSession(Long userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessionsByUser.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) sessionsByUser.remove(userId, userSessions);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.remove(session);
        Object userId = session.getAttributes().remove("notificationUserId");
        if (userId instanceof Long id) removeSession(id, session);
    }
}
