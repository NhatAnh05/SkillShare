package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.ConversationRepository;
import com.skillrental.repository.MessageRepository;
import com.skillrental.repository.UserRepository;
import com.skillrental.service.ChatService;
import com.skillrental.websocket.ChatWebSocketHandler;
import com.skillrental.notification.NotificationWebSocketHandler;
import com.skillrental.repository.NotificationRepository;
import com.skillrental.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ChatRealtimeIntegrationTest {
    @Autowired private ChatWebSocketHandler webSocket;
    @Autowired private NotificationWebSocketHandler notificationWebSocket;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ChatService chat;
    @Autowired private UserRepository users;
    @Autowired private ConversationRepository conversations;
    @Autowired private MessageRepository messages;
    @Autowired private MockMvc mvc;
    private WebSocketSession customerSession;
    private WebSocketSession providerSession;

    @AfterEach
    void disconnectSessions() {
        if (customerSession != null) webSocket.afterConnectionClosed(customerSession,
                org.springframework.web.socket.CloseStatus.NORMAL);
        if (providerSession != null) webSocket.afterConnectionClosed(providerSession,
                org.springframework.web.socket.CloseStatus.NORMAL);
    }

    @Test
    void subscribedCustomerAndProviderExchangePersistedRealtimeMessages() throws Exception {
        User customer = createUser(Role.CUSTOMER);
        User provider = createUser(Role.PROVIDER);
        Conversation conversation = conversations.saveAndFlush(Conversation.builder()
                .customer(customer).provider(provider).build());
        customerSession = session(customer);
        providerSession = session(provider);

        sendFrame(customerSession, "{\"type\":\"subscribe\",\"conversationId\":" + conversation.getId() + "}");
        sendFrame(providerSession, "{\"type\":\"subscribe\",\"conversationId\":" + conversation.getId() + "}");
        sendFrame(customerSession, "{\"type\":\"send\",\"conversationId\":" + conversation.getId()
                + ",\"content\":\"Xin chào realtime\"}");
        sendFrame(providerSession, "{\"type\":\"send\",\"conversationId\":" + conversation.getId()
                + ",\"content\":\"Phản hồi realtime\"}");

        Message saved = messages.findByConversationOrderBySentAtAsc(conversation).stream()
                .filter(item -> item.getContent().equals("Xin chào realtime")).findFirst().orElseThrow();
        assertEquals(customer.getId(), saved.getSender().getId());
        assertTrue(capturedText(providerSession).contains("Xin chào realtime"));
        assertTrue(capturedText(providerSession).contains("\"type\":\"message\""));
        assertTrue(capturedText(customerSession).contains("Phản hồi realtime"));
        assertTrue(chat.messages(conversation).stream().anyMatch(item -> item.getId().equals(saved.getId())));
    }

    @Test
    void anotherUserCannotSubscribeOrReadConversationAndUnreadCountClearsOnRead() throws Exception {
        User customer = createUser(Role.CUSTOMER);
        User provider = createUser(Role.PROVIDER);
        User outsider = createUser(Role.CUSTOMER);
        Conversation conversation = conversations.saveAndFlush(Conversation.builder()
                .customer(customer).provider(provider).build());
        WebSocketSession outsiderSession = session(outsider);
        sendFrame(outsiderSession, "{\"type\":\"subscribe\",\"conversationId\":" + conversation.getId() + "}");
        sendFrame(outsiderSession, "{\"type\":\"send\",\"conversationId\":" + conversation.getId()
                + ",\"content\":\"Unauthorized message\"}");
        assertTrue(capturedText(outsiderSession).contains("\"type\":\"error\""));
        assertFalse(messages.findByConversationOrderBySentAtAsc(conversation).stream()
                .anyMatch(item -> item.getContent().equals("Unauthorized message")));
        webSocket.afterConnectionClosed(outsiderSession, org.springframework.web.socket.CloseStatus.NORMAL);

        Message received = chat.send(conversation, provider, "Tin nhắn chưa đọc");
        assertEquals(1, chat.unreadCount(conversation, customer));
        mvc.perform(get("/chat/conversations").with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unreadCount").value(1));
        mvc.perform(post("/chat/{id}/read", conversation.getId()).with(csrf())
                        .with(user(customer.getEmail()).roles("CUSTOMER"))).andExpect(status().isOk());
        mvc.perform(get("/chat/{id}/messages", conversation.getId())
                        .with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].read").value(true));
        assertEquals(0, chat.unreadCount(conversation, customer));
        mvc.perform(get("/chat/conversations").with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unreadCount").value(0));
        mvc.perform(get("/chat/{id}/messages", conversation.getId())
                        .with(user(outsider.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportThreadsAreVisibleOnlyToTheirRequesterAndAdmin() throws Exception {
        User customer = createUser(Role.CUSTOMER);
        User provider = createUser(Role.PROVIDER);
        User admin = users.findByEmailIgnoreCase("admin@skillrental.vn").orElseThrow();
        Conversation privateConversation = conversations.saveAndFlush(Conversation.builder()
                .customer(customer).provider(provider).build());
        var result = mvc.perform(post("/chat/support/start").with(csrf())
                        .with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andReturn();
        Long supportId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString()).get("conversationId").asLong();
        Conversation supportConversation = conversations.findById(supportId).orElseThrow();
        assertTrue(Boolean.TRUE.equals(supportConversation.getSupportConversation()));
        assertTrue(chat.isParticipant(supportConversation, customer));
        assertTrue(chat.isParticipant(supportConversation, admin));
        assertFalse(chat.isParticipant(privateConversation, admin));
        assertThrows(IllegalArgumentException.class, () -> chat.requireParticipant(privateConversation.getId(), admin));
        mvc.perform(get("/admin/support").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void notificationsPersistBroadcastToTheRecipientAndRemainPrivate() throws Exception {
        User customer = createUser(Role.CUSTOMER);
        User provider = createUser(Role.PROVIDER);
        WebSocketSession notificationSession = session(customer);
        notificationWebSocket.afterConnectionEstablished(notificationSession);
        var saved = notificationService.create(customer, "ORDER_STATUS", "Đơn hàng cập nhật",
                "Đơn hàng đang chờ bạn.", "ORDER", 1L, "/orders/1/progress");
        var frames = org.mockito.ArgumentCaptor.forClass(org.springframework.web.socket.WebSocketMessage.class);
        verify(notificationSession, atLeastOnce()).sendMessage(frames.capture());
        assertTrue(frames.getAllValues().stream()
                .map(frame -> ((TextMessage) frame).getPayload())
                .anyMatch(payload -> payload.contains("\"type\":\"notification\"")
                        && payload.contains("\"id\":" + saved.getId())));
        mvc.perform(get("/notifications").with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Đơn hàng cập nhật"));
        mvc.perform(get("/notifications").with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(post("/notifications/{id}/read", saved.getId()).with(csrf())
                        .with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
        assertNotNull(notificationRepository.findById(saved.getId()).orElseThrow().getReadAt());
        notificationWebSocket.afterConnectionClosed(notificationSession,
                org.springframework.web.socket.CloseStatus.NORMAL);
    }

    @Test
    void policyAndInboxPagesRenderAndMarkAllReadIsRecipientScoped() throws Exception {
        User customer = createUser(Role.CUSTOMER);
        User other = createUser(Role.CUSTOMER);
        var first = notificationService.create(customer, "ORDER_STATUS", "Đơn hàng A",
                "Thông báo A", "ORDER", 1L, "/orders/1/progress");
        var second = notificationService.create(customer, "ORDER_STATUS", "Đơn hàng B",
                "Thông báo B", "ORDER", 2L, "/orders/2/progress");
        var third = notificationService.create(other, "ORDER_STATUS", "Riêng tư",
                "Thông báo của tài khoản khác", "ORDER", 3L, "/orders/3/progress");

        mvc.perform(get("/policies/terms"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Điều khoản sử dụng")));
        mvc.perform(get("/messages").with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tin nhắn")));
        mvc.perform(get("/notifications/all").with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đơn hàng A")));
        mvc.perform(post("/notifications/read-all").with(csrf())
                        .with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
        assertNotNull(notificationRepository.findById(first.getId()).orElseThrow().getReadAt());
        assertNotNull(notificationRepository.findById(second.getId()).orElseThrow().getReadAt());
        assertNull(notificationRepository.findById(third.getId()).orElseThrow().getReadAt());
    }

    private User createUser(Role role) {
        String identity = UUID.randomUUID().toString().substring(0, 8);
        return users.saveAndFlush(User.builder()
                .fullName("Chat Test " + identity)
                .email("chat-" + identity + "@skillrental.test")
                .password("not-used")
                .role(role)
                .emailVerified(true)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private WebSocketSession session(User user) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String,Object> attributes = new ConcurrentHashMap<>();
        Principal principal = () -> user.getEmail();
        when(session.getPrincipal()).thenReturn(principal);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private void sendFrame(WebSocketSession session, String payload) throws Exception {
        webSocket.handleMessage(session, new TextMessage(payload));
    }

    private String capturedText(WebSocketSession session) throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.web.socket.WebSocketMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(message -> ((TextMessage) message).getPayload())
                .reduce("", String::concat);
    }
}
