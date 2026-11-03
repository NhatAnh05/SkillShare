package com.skillrental;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.skillrental.repository.UserRepository;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP logins/cookies and real WebSocket handshakes; not a replacement for browser UI QA. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:live_sessions;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class NotificationLiveSessionsTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    String base() { return "http://127.0.0.1:" + port; }

    @Test
    void threeIndependentLoginsReceiveLiveMessagesAndOfflineInboxSurvivesLogin() throws Exception {
        Session admin = login("admin@skillrental.vn");
        Session customer = login("customer@skillrental.vn");
        Session provider = login("provider1@skillrental.vn");
        assertNotEquals(admin.cookies(), customer.cookies());
        assertNotEquals(provider.cookies(), customer.cookies());
        SocketInbox customerInbox = new SocketInbox();
        SocketInbox providerInbox = new SocketInbox();
        WebSocket customerSocket = connect(customer, customerInbox);
        WebSocket providerSocket = connect(provider, providerInbox);
        SocketInbox secondCustomerTab = new SocketInbox();
        WebSocket secondCustomerSocket = connect(customer, secondCustomerTab);
        try {
            customerInbox.await("snapshot"); providerInbox.await("snapshot"); secondCustomerTab.await("snapshot");
            var customerUser = users.findByEmailIgnoreCase("customer@skillrental.vn").orElseThrow();
            var providerUser = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
            String unique = "Live sessions " + UUID.randomUUID();
            Map<String,Object> request = new LinkedHashMap<>(Map.of("title", unique, "content", "Thông báo thực tế ba phiên",
                    "type", "ADMIN_GENERAL", "audience", "SELECTED",
                    "recipientIds", List.of(customerUser.getId(), providerUser.getId()), "requestKey", UUID.randomUUID().toString()));
            JsonNode sent = post(admin, "/admin/notifications/send", request, 202);
            JsonNode customerEvent = customerInbox.await("notification");
            JsonNode providerEvent = providerInbox.await("notification");
            assertEquals(unique, customerEvent.get("title").asText());
            assertEquals(unique, providerEvent.get("title").asText());
            assertFalse(customerEvent.get("read").asBoolean());
            assertFalse(providerEvent.get("read").asBoolean());
            assertEquals(1, getJson(customer, "/notifications").get("unreadCount").asInt());
            assertEquals(1, getJson(provider, "/notifications").get("unreadCount").asInt());
            assertEquals(0, getJson(admin, "/notifications").get("items").size());
            long notificationId = customerEvent.get("id").asLong();
            assertEquals(404, get(provider, "/notifications/item/" + notificationId).statusCode());
            post(customer, "/notifications/" + notificationId + "/read", Map.of(), 200);
            assertEquals(0, customerInbox.await("read").get("unreadCount").asInt());
            assertEquals(0, secondCustomerTab.await("read").get("unreadCount").asInt());
            JsonNode detail = getJson(admin, "/admin/notifications/campaigns/" + sent.get("campaignId").asLong());
            assertEquals(1, detail.get("campaign").get("readCount").asInt());
            assertEquals(1, detail.get("campaign").get("unreadCount").asInt());
            // Duplicate HTTP submission uses the same key, with one row per recipient.
            assertEquals(sent.get("campaignId"), post(admin, "/admin/notifications/send", request, 202).get("campaignId"));
            providerSocket.sendClose(WebSocket.NORMAL_CLOSURE, "offline").get(5, TimeUnit.SECONDS);
            request.put("title", unique + " offline");
            request.put("requestKey", UUID.randomUUID().toString());
            request.put("recipientIds", List.of(providerUser.getId()));
            post(admin, "/admin/notifications/send", request, 202);
            Session providerAgain = login("provider1@skillrental.vn");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            JsonNode inbox;
            do {
                inbox = getJson(providerAgain, "/notifications");
                if (inbox.get("unreadCount").asInt() == 2) break;
                Thread.sleep(25);
            } while (System.nanoTime() < deadline);
            assertEquals(2, inbox.get("unreadCount").asInt());
            assertEquals(unique + " offline", inbox.get("items").get(0).get("title").asText());
            post(providerAgain, "/notifications/read-all", Map.of(), 200);
            assertEquals(0, getJson(providerAgain, "/notifications").get("unreadCount").asInt());
            assertEquals(403, postRaw(customer, "/admin/notifications/send", request).statusCode());
            assertEquals(403, postRaw(providerAgain, "/admin/notifications/send", request).statusCode());
        } finally {
            customerSocket.abort(); providerSocket.abort(); secondCustomerSocket.abort();
        }
    }

    Session login(String email) throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        Session session = new Session(client, cookies);
        String page = get(session, "/login").body();
        var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page);
        assertTrue(token.find(), "Login CSRF field");
        String form = "username=" + URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8)
                + "&password=123456&_csrf=" + URLEncoder.encode(token.group(1), java.nio.charset.StandardCharsets.UTF_8);
        var result = client.send(HttpRequest.newBuilder(URI.create(base()+"/login"))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, result.statusCode());
        assertFalse(result.headers().firstValue("location").orElse("").contains("error"));
        return session;
    }
    HttpResponse<String> get(Session session, String path) throws Exception {
        return session.client.send(HttpRequest.newBuilder(URI.create(base()+path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonNode getJson(Session session, String path) throws Exception {
        var result=get(session,path);assertEquals(200,result.statusCode());return mapper.readTree(result.body());
    }
    HttpResponse<String> postRaw(Session session, String path, Object body) throws Exception {
        String page=get(session,"/notifications/all").body();
        var token=Pattern.compile("name=\"_csrf\"\\s+content=\"([^\"]+)\"").matcher(page);
        assertTrue(token.find(), "Header CSRF meta");
        return session.client.send(HttpRequest.newBuilder(URI.create(base()+path)).header("Content-Type","application/json")
                .header("X-CSRF-TOKEN",token.group(1)).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    JsonNode post(Session session,String path,Object body,int status) throws Exception {
        var result=postRaw(session,path,body);assertEquals(status,result.statusCode(),result.body());return mapper.readTree(result.body());
    }
    WebSocket connect(Session session,SocketInbox inbox) {
        return session.client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws/notifications"),inbox).join();
    }
    record Session(HttpClient client,CookieManager jar) {
        String cookies() { return jar.getCookieStore().getCookies().toString(); }
    }
    class SocketInbox implements WebSocket.Listener {
        final BlockingQueue<String> messages=new LinkedBlockingQueue<>();
        final StringBuilder partial=new StringBuilder();
        public void onOpen(WebSocket socket) { socket.request(1); }
        public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last) {
            partial.append(data);if(last){messages.add(partial.toString());partial.setLength(0);}socket.request(1);return null;
        }
        JsonNode await(String type) throws Exception {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
            while(System.nanoTime()<deadline) {
                String value=messages.poll(1,TimeUnit.SECONDS);if(value==null)continue;
                JsonNode item=mapper.readTree(value);if(type.equals(item.path("type").asText()))return item;
            }
            fail("No WebSocket frame of type "+type);return null;
        }
    }
}
