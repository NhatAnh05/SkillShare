package com.skillrental;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillrental.dto.AdminNotificationRequest;
import com.skillrental.entity.*;
import com.skillrental.notification.NotificationWebSocketHandler;
import com.skillrental.repository.NotificationCampaignRecipientRepository;
import com.skillrental.repository.NotificationCampaignRepository;
import com.skillrental.repository.NotificationRepository;
import com.skillrental.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminNotificationCampaignTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private NotificationRepository notifications;
    @Autowired private NotificationCampaignRepository campaigns;
    @Autowired private NotificationCampaignRecipientRepository recipients;
    @Autowired private NotificationWebSocketHandler webSocket;

    private WebSocketSession onlineSession;

    @AfterEach
    void disconnect() {
        if (onlineSession != null) {
            webSocket.afterConnectionClosed(onlineSession,
                    org.springframework.web.socket.CloseStatus.NORMAL);
        }
    }

    @Test
    void adminCanTargetSelectedUserOnceAndOnlineRecipientGetsRealtimeNotification() throws Exception {
        User admin = admin();
        User customer = createUser(Role.CUSTOMER, AccountStatus.ACTIVE, true);
        onlineSession = session(customer);
        webSocket.afterConnectionEstablished(onlineSession);

        AdminNotificationRequest request = request("SELECTED", "ADMIN_IMPORTANT",
                java.util.List.of(customer.getId()), UUID.randomUUID().toString());
        String body = mapper.writeValueAsString(request);
        JsonNode first = mapper.readTree(mvc.perform(post("/admin/notifications/send")
                        .with(csrf()).with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        JsonNode duplicate = mapper.readTree(mvc.perform(post("/admin/notifications/send")
                        .with(csrf()).with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());

        Long campaignId = first.get("campaignId").asLong();
        assertEquals(campaignId, duplicate.get("campaignId").asLong());
        assertEquals(1, recipients.findByCampaignIdOrderByIdAsc(campaignId).size());
        await(() -> notifications.findTop50ByRecipientOrderByCreatedAtDescIdDesc(customer).stream()
                .anyMatch(item -> item.getTitle().equals(request.title())));
        Notification saved = notifications.findTop50ByRecipientOrderByCreatedAtDescIdDesc(customer).stream()
                .filter(item -> item.getTitle().equals(request.title())).findFirst().orElseThrow();
        assertEquals(admin.getId(), saved.getSender().getId());
        assertEquals("/notifications/item/" + saved.getId(), saved.getTargetUrl());
        verify(onlineSession, atLeastOnce()).sendMessage(any());

        mvc.perform(get("/notifications").with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sender").value(admin.getFullName()));
        mvc.perform(post("/notifications/{id}/read", saved.getId()).with(csrf())
                        .with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(get("/notifications/item/{id}", saved.getId())
                        .with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(request.title())));

        mvc.perform(post("/admin/notifications/send").with(csrf())
                        .with(user(customer.getEmail()).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/notifications/recipients?q=" + customer.getEmail())
                        .with(user(customer.getEmail()).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void audiencePreviewAndCampaignsCoverEachGroupWithoutAdminOrDuplicateRecipients() throws Exception {
        User admin = admin();
        User customer1 = createUser(Role.CUSTOMER, AccountStatus.ACTIVE, true);
        User customer2 = createUser(Role.CUSTOMER, AccountStatus.ACTIVE, true);
        User provider = createUser(Role.PROVIDER, AccountStatus.ACTIVE, true);
        User lockedCustomer = createUser(Role.CUSTOMER, AccountStatus.LOCKED, true);
        User unverifiedProvider = createUser(Role.PROVIDER, AccountStatus.ACTIVE, false);

        long customerCount = preview(admin, "ALL_CUSTOMERS", java.util.List.of());
        long providerCount = preview(admin, "ALL_PROVIDERS", java.util.List.of());
        long bothCount = preview(admin, "ALL_MARKETPLACE_USERS", java.util.List.of());
        assertEquals(customerCount + providerCount, bothCount);

        Long allCustomers = send(admin, "ALL_CUSTOMERS", java.util.List.of());
        Long allProviders = send(admin, "ALL_PROVIDERS", java.util.List.of());
        Long allUsers = send(admin, "ALL_MARKETPLACE_USERS", java.util.List.of());
        Long selected = send(admin, "SELECTED", java.util.List.of(customer1.getId(), provider.getId()));

        var customerRows = recipients.findByCampaignIdOrderByIdAsc(allCustomers);
        var providerRows = recipients.findByCampaignIdOrderByIdAsc(allProviders);
        var bothRows = recipients.findByCampaignIdOrderByIdAsc(allUsers);
        var selectedRows = recipients.findByCampaignIdOrderByIdAsc(selected);
        assertEquals(customerCount, customerRows.size());
        assertEquals(providerCount, providerRows.size());
        assertEquals(bothCount, bothRows.size());
        assertEquals(2, selectedRows.size());
        assertTrue(customerRows.stream().allMatch(row -> row.getRecipient().getRole() == Role.CUSTOMER));
        assertTrue(providerRows.stream().allMatch(row -> row.getRecipient().getRole() == Role.PROVIDER));
        assertTrue(bothRows.stream().allMatch(row -> row.getRecipient().getRole() != Role.ADMIN));
        assertEquals(bothRows.size(), bothRows.stream().map(row -> row.getRecipient().getId()).distinct().count());
        assertFalse(customerRows.stream().anyMatch(row -> row.getRecipient().getId().equals(lockedCustomer.getId())));
        assertFalse(providerRows.stream().anyMatch(row -> row.getRecipient().getId().equals(unverifiedProvider.getId())));
        assertEquals(2, selectedRows.stream().map(row -> row.getRecipient().getId()).distinct().count());
        await(() -> recipients.findByCampaignIdOrderByIdAsc(selected).stream()
                .allMatch(row -> row.getNotification() != null
                        && !"STORED".equals(row.getDeliveryStatus())));
        var deliveredSelected = recipients.findByCampaignIdOrderByIdAsc(selected);
        assertEquals("OFFLINE", deliveredSelected.stream()
                .filter(row -> row.getRecipient().getId().equals(provider.getId()))
                .findFirst().orElseThrow().getDeliveryStatus());

        mvc.perform(post("/admin/notifications/preview").with(csrf())
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("audience", "SELECTED",
                                "recipientIds", java.util.List.of(customer1.getId(), customer1.getId())))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/notifications/send").with(csrf())
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request("SELECTED", "ADMIN_GENERAL",
                                java.util.List.of(lockedCustomer.getId()), UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/notifications/send").with(csrf())
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(requestWithTarget("SELECTED", "ADMIN_GENERAL",
                                java.util.List.of(customer1.getId()), UUID.randomUUID().toString(), "https://example.com"))))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/admin/notifications").with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Quản lý thông báo")));
        mvc.perform(get("/admin/notifications/campaigns/{id}", selected)
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients.length()").value(2));
        mvc.perform(post("/notifications/read-all").with(csrf())
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
        assertNull(deliveredSelected.stream().filter(row -> row.getRecipient().getId().equals(customer1.getId()))
                .findFirst().orElseThrow().getNotification().getReadAt());
    }

    @Autowired private com.skillrental.service.NotificationCampaignService campaignService;
    @Autowired private com.skillrental.service.NotificationCampaignDeliveryService deliveryService;
    @Autowired private com.skillrental.repository.NotificationCampaignAuditRepository audit;

    @Test
    void concurrentDoubleSubmitAndDuplicateWorkersCreateOneNotification() throws Exception {
        User admin = admin();
        User customer = createUser(Role.CUSTOMER, AccountStatus.ACTIVE, true);
        var request = request("SELECTED", "ADMIN_GENERAL", java.util.List.of(customer.getId()), UUID.randomUUID().toString());
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); return campaignService.send(admin, request).getId(); });
            var second = executor.submit(() -> { start.await(); return campaignService.send(admin, request).getId(); });
            start.countDown();
            Long id = first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(id, second.get(15, java.util.concurrent.TimeUnit.SECONDS));
            var rows = recipients.findByCampaignIdOrderByIdAsc(id);
            assertEquals(1, rows.size());
            Long rowId = rows.get(0).getId();
            var worker1 = executor.submit(() -> deliveryService.deliverQueued(rowId));
            var worker2 = executor.submit(() -> deliveryService.deliverQueued(rowId));
            worker1.get(15, java.util.concurrent.TimeUnit.SECONDS);
            worker2.get(15, java.util.concurrent.TimeUnit.SECONDS);
            await(() -> recipients.findById(rowId).orElseThrow().getNotification() != null);
            assertEquals(1, notifications.findTop50ByRecipientOrderByCreatedAtDescIdDesc(customer).stream()
                    .filter(n -> n.getTitle().equals(request.title())).count());
            assertEquals(1, audit.findByCampaignIdOrderByIdAsc(id).size());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void retryWebSocketFailureKeepsNotificationAndAuditsRetry() throws Exception {
        User admin = admin();
        User customer = createUser(Role.CUSTOMER, AccountStatus.ACTIVE, true);
        Long id = send(admin, "SELECTED", java.util.List.of(customer.getId()));
        await(() -> "OFFLINE".equals(recipients.findByCampaignIdOrderByIdAsc(id).get(0).getDeliveryStatus()));
        var row = recipients.findByCampaignIdOrderByIdAsc(id).get(0);
        Long notificationId = row.getNotification().getId();
        deliveryService.recordWebSocketAttempt(notificationId, 0, 1);
        mvc.perform(post("/admin/notifications/campaigns/{id}/retry", id).with(csrf())
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk());
        await(() -> "OFFLINE".equals(recipients.findById(row.getId()).orElseThrow().getDeliveryStatus()));
        var retried = recipients.findById(row.getId()).orElseThrow();
        assertEquals(notificationId, retried.getNotification().getId());
        assertNull(retried.getNotification().getReadAt());
        assertEquals(2, audit.findByCampaignIdOrderByIdAsc(id).size());
        assertEquals("RETRY", audit.findByCampaignIdOrderByIdAsc(id).get(1).getAction());
    }

    @Test
    void invalidLinksHtmlMissingCsrfAndUnauthorizedRecipientsAreRejected() throws Exception {
        User admin = admin();
        User customer = createUser(Role.CUSTOMER, AccountStatus.ACTIVE, true);
        User provider = createUser(Role.PROVIDER, AccountStatus.ACTIVE, true);
        for (String link : java.util.List.of("//evil.test", "/%2f%2fevil.test", "/logout", "/unknown",
                "/policies/../logout", "/marketplace?next=https://evil.test", "/\\evil.test")) {
            var req = requestWithTarget("SELECTED", "ADMIN_GENERAL", java.util.List.of(customer.getId()),
                    UUID.randomUUID().toString(), link);
            mvc.perform(post("/admin/notifications/send").with(csrf()).with(user(admin.getEmail()).roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
        var html = new AdminNotificationRequest("<script>alert(1)</script>", "Nội dung", "ADMIN_GENERAL", "SELECTED",
                java.util.List.of(customer.getId()), null, UUID.randomUUID().toString());
        mvc.perform(post("/admin/notifications/send").with(csrf()).with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(html)))
                .andExpect(status().isBadRequest());
        String body = mapper.writeValueAsString(request("SELECTED", "ADMIN_GENERAL",
                java.util.List.of(customer.getId()), UUID.randomUUID().toString()));
        mvc.perform(post("/admin/notifications/send").with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        for (User actor : java.util.List.of(customer, provider)) {
            mvc.perform(post("/admin/notifications/send").with(csrf()).with(user(actor.getEmail()).roles(actor.getRole().name()))
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
            mvc.perform(get("/admin/notifications/campaigns/1").with(user(actor.getEmail()).roles(actor.getRole().name())))
                    .andExpect(status().isForbidden());
        }
        // A forged ADMIN role does not match the persisted CUSTOMER role:
        // the active-session filter logs out the request and redirects to login.
        mvc.perform(post("/admin/notifications/send").with(csrf()).with(user(customer.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?roleChanged"));
        mvc.perform(post("/admin/notifications/send").with(csrf()).with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request("SELECTED",
                                "ADMIN_GENERAL", java.util.List.of(admin.getId()), UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneDigitIdSearchAndNotificationOwnershipAreEnforced() throws Exception {
        User admin = admin();
        User seeded = users.findByEmailIgnoreCase("customer@skillrental.vn").orElseThrow();
        mvc.perform(get("/admin/notifications/recipients").param("q", seeded.getId().toString())
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.hasItem(seeded.getId().intValue())));
        User customer = createUser(Role.CUSTOMER, AccountStatus.ACTIVE, true);
        User outsider = createUser(Role.PROVIDER, AccountStatus.ACTIVE, true);
        Long id = send(admin, "SELECTED", java.util.List.of(customer.getId()));
        await(() -> recipients.findByCampaignIdOrderByIdAsc(id).get(0).getNotification() != null);
        Long notificationId = recipients.findByCampaignIdOrderByIdAsc(id).get(0).getNotification().getId();
        mvc.perform(get("/notifications/item/{id}", notificationId).with(user(outsider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/notifications/{id}/read", notificationId).with(csrf()).with(user(outsider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isNotFound());
        assertNull(notifications.findById(notificationId).orElseThrow().getReadAt());
        mvc.perform(get("/notifications").with(user(outsider.getEmail()).roles("PROVIDER")))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void failedCreationCanBeRetriedWithoutChangingTheRecipientSnapshot() throws Exception {
        User admin = admin();
        User customer = createUser(Role.CUSTOMER, AccountStatus.ACTIVE, true);
        var campaign = campaigns.saveAndFlush(NotificationCampaign.builder().sender(admin)
                .requestKey(UUID.randomUUID().toString()).title("Retry failed creation")
                .content("Persist then retry").type("ADMIN_GENERAL").audience("SELECTED").build());
        var row = recipients.saveAndFlush(NotificationCampaignRecipient.builder().campaign(campaign)
                .recipient(customer).deliveryStatus("FAILED").lastError("Simulated delivery failure").build());
        mvc.perform(post("/admin/notifications/campaigns/{id}/retry", campaign.getId()).with(csrf())
                        .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk());
        await(() -> "OFFLINE".equals(recipients.findById(row.getId()).orElseThrow().getDeliveryStatus()));
        var stored = recipients.findById(row.getId()).orElseThrow();
        assertNotNull(stored.getNotification());
        assertNull(stored.getLastError());
        assertEquals(1, recipients.countByCampaignId(campaign.getId()));
        assertEquals(customer.getId(), stored.getNotification().getRecipient().getId());
        assertEquals("RETRY", audit.findByCampaignIdOrderByIdAsc(campaign.getId()).get(0).getAction());
    }

    @Test
    void allPolicyDraftsArePublicAndContainRequestedContent() throws Exception {
        for (String slug : java.util.List.of("terms", "privacy", "provider", "booking", "cancellation", "messages", "payments", "cookies")) {
            mvc.perform(get("/policies/" + slug)).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-11-01-v2")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("bản dự thảo nội dung")));
        }
        mvc.perform(get("/policies/messages")).andExpect(content().string(org.hamcrest.Matchers.containsString("ghi nhận lý do")));
    }

    private long preview(User admin, String audience, java.util.List<Long> ids) throws Exception {
        String response = mvc.perform(post("/admin/notifications/preview").with(csrf())
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("audience", audience, "recipientIds", ids))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("recipientCount").asLong();
    }

    private Long send(User admin, String audience, java.util.List<Long> ids) throws Exception {
        String response = mvc.perform(post("/admin/notifications/send").with(csrf())
                        .with(user(admin.getEmail()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request(audience, "ADMIN_GENERAL", ids,
                                UUID.randomUUID().toString()))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("campaignId").asLong();
    }

    private AdminNotificationRequest request(String audience, String type, java.util.List<Long> ids, String key) {
        return new AdminNotificationRequest("Thông báo " + key, "Nội dung gửi thử", type, audience, ids, null, key);
    }

    private AdminNotificationRequest requestWithTarget(String audience, String type, java.util.List<Long> ids,
                                                       String key, String target) {
        return new AdminNotificationRequest("Thông báo " + key, "Nội dung gửi thử", type, audience, ids, target, key);
    }

    private User admin() {
        return users.findByEmailIgnoreCase("admin@skillrental.vn").orElseThrow();
    }

    private User createUser(Role role, AccountStatus status, boolean verified) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return users.saveAndFlush(User.builder()
                .fullName("Campaign Test " + unique)
                .email("campaign-" + unique + "@skillrental.test")
                .password("not-used")
                .role(role)
                .accountStatus(status)
                .emailVerified(verified)
                .build());
    }

    private WebSocketSession session(User user) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        Principal principal = user::getEmail;
        when(session.getPrincipal()).thenReturn(principal);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(25);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for queued notification delivery.");
    }

}
