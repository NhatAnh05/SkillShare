package com.skillrental.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.skillrental.entity.Conversation;
import com.skillrental.entity.User;
import com.skillrental.repository.UserRepository;
import com.skillrental.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final String CONVERSATIONS_ATTRIBUTE = "chatConversationIds";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private final ObjectMapper objectMapper;
    private final UserRepository users;
    private final ChatService chat;
    private final SocketSessionRegistry sessionRegistry;
    private final com.skillrental.repository.ConversationRepository conversationRepository;
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> userSessions=new ConcurrentHashMap<>();
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if(session.getPrincipal()==null){session.close(CloseStatus.NOT_ACCEPTABLE);return;}
        var user=users.findByEmailIgnoreCase(session.getPrincipal().getName()).orElseThrow();
        if(user.getAccountStatus()!=com.skillrental.entity.AccountStatus.ACTIVE){session.close(CloseStatus.POLICY_VIOLATION);return;}
        register(session,user);
        sessionRegistry.register(session);
    }
    private void register(WebSocketSession session,User user) {
        session.getAttributes().put("chatUserId",user.getId());
        userSessions.computeIfAbsent(user.getId(),ignored->ConcurrentHashMap.newKeySet()).add(session);
    }
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        try {
            Principal principal = session.getPrincipal();
            if (principal == null) throw new IllegalArgumentException("Cần đăng nhập để chat");
            User user = users.findByEmailIgnoreCase(principal.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
            if(user.getAccountStatus()!=com.skillrental.entity.AccountStatus.ACTIVE||!Boolean.TRUE.equals(user.getEmailVerified()))throw new IllegalArgumentException("Tài khoản không còn được phép chat.");
            register(session,user);
            JsonNode payload = objectMapper.readTree(message.getPayload());
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("Dữ liệu chat không hợp lệ");
            }
            String type = payload.path("type").asText();
            long conversationId = payload.path("conversationId").asLong(-1);
            if (conversationId < 1) throw new IllegalArgumentException("Mã hội thoại không hợp lệ");
            Conversation conversation = chat.requireParticipant(conversationId, user);
            switch (type) {
                case "subscribe" -> subscribe(session, conversation);
                case "send" -> {
                    if (!subscribedTo(session, conversationId)) {
                        throw new IllegalArgumentException("Hãy tham gia hội thoại trước khi gửi tin");
                    }
                    chat.send(conversation, user, payload.path("content").asText(),payload.path("clientKey").asText(null));
                }
                case "read" -> {
                    if (!subscribedTo(session, conversationId)) {
                        throw new IllegalArgumentException("Hãy tham gia hội thoại trước");
                    }
                    chat.markRead(conversation, user);
                }
                default -> throw new IllegalArgumentException("Loại sự kiện chat không hợp lệ");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendError(session, ex.getMessage());
        } catch (JsonProcessingException ex) {
            sendError(session, "Dữ liệu chat không hợp lệ");
        } catch (DataAccessException ex) {
            sendError(session, "Không thể lưu tin nhắn lúc này");
        }
    }

    @TransactionalEventListener
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,readOnly=true)
    public void onMessageSent(ChatMessageSentEvent event) {
        broadcast(event.conversationId(), Map.of(
                "type", "message",
                "id", event.messageId(),
                "conversationId", event.conversationId(),
                "senderId", event.senderId(),
                "sender", event.senderName(),
                "content", event.content(),
                "time", event.sentAt().format(TIME_FORMAT),
                "read", false));
    }

    @TransactionalEventListener
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,readOnly=true)
    public void onMessagesRead(ChatReadEvent event) {
        broadcast(event.conversationId(), Map.of(
                "type", "read",
                "conversationId", event.conversationId(),
                "readerId", event.readerId()));
    }

    @TransactionalEventListener public void membershipChanged(ChatMembershipChangedEvent event){
        for(Long id:event.userIds()){var set=userSessions.get(id);if(set!=null)for(var session:set)try{send(session,Map.of("type","inbox","conversationId",event.conversationId()));}catch(IOException|IllegalStateException ex){set.remove(session);}}
    }
    private void subscribe(WebSocketSession session, Conversation conversation) throws IOException {
        @SuppressWarnings("unchecked")
        Set<Long> conversationIds = (Set<Long>) session.getAttributes().computeIfAbsent(
                CONVERSATIONS_ATTRIBUTE, ignored -> ConcurrentHashMap.newKeySet());
        conversationIds.add(conversation.getId());
        subscribers.computeIfAbsent(conversation.getId(), ignored -> ConcurrentHashMap.newKeySet()).add(session);
        send(session, Map.of("type", "subscribed", "conversationId", conversation.getId()));
    }

    private boolean subscribedTo(WebSocketSession session, long conversationId) {
        Object value = session.getAttributes().get(CONVERSATIONS_ATTRIBUTE);
        return value instanceof Set<?> ids && ids.contains(conversationId);
    }

    private void broadcast(long conversationId, Map<String, ?> event) {
        var conversation=chatConversation(conversationId);
        if(conversation==null)return;
        // Check current membership on every event: a previously assigned Admin loses access immediately.
        userSessions.forEach((userId,sessions)->{
            var user=users.findById(userId).orElse(null);
            if(user==null || user.getAccountStatus()!=com.skillrental.entity.AccountStatus.ACTIVE || !chat.isParticipant(conversation,user))return;
            for(var session:sessions)try {
                var personalized=new java.util.HashMap<String,Object>(event);
                if(event.get("senderId")!=null)personalized.put("own",userId.equals(event.get("senderId")));
                send(session,personalized);
            }catch(IOException|IllegalStateException ex){sessions.remove(session);}
        });
    }
    private Conversation chatConversation(long id) {
        return conversationRepository.findById(id).orElse(null);
    }

    private void sendError(WebSocketSession session, String message) {
        if (session.isOpen()) {
            try {
                send(session, Map.of("type", "error", "message", message));
            } catch (IOException ignored) {
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignoredClose) {
                    return;
                }
            }
        }
    }

    private void send(WebSocketSession session, Map<String, ?> payload) throws IOException {
        synchronized (session) {
            if (session.isOpen()) session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        }
    }

    private void removeSubscriber(long conversationId, WebSocketSession session) {
        Set<WebSocketSession> sessions = subscribers.get(conversationId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) subscribers.remove(conversationId, sessions);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.remove(session);
        Object userId=session.getAttributes().remove("chatUserId");
        if(userId instanceof Long id){var set=userSessions.get(id);if(set!=null){set.remove(session);if(set.isEmpty())userSessions.remove(id,set);}}
        Object subscribedIds = session.getAttributes().remove(CONVERSATIONS_ATTRIBUTE);
        if (subscribedIds instanceof Set<?> ids) {
            ids.forEach(id -> {
                if (id instanceof Long conversationId) removeSubscriber(conversationId, session);
            });
        }
    }
}
