package com.skillrental;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkspaceControllerSmokeTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProviderProfileRepository providerProfiles;

    @Autowired
    private ServiceRequestRepository serviceRequests;

    @Autowired
    private SkillRepository skills;

    @Autowired
    private RentalOrderRepository orders;

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private ComplaintRepository complaints;

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserWalletRepository wallets;

    @Autowired
    private WorkspaceState workspaceState;

    @Test
    @WithMockUser(username = "provider1@skillrental.vn", roles = "PROVIDER")
    void allProviderGetRoutesRenderWithoutServerError() throws Exception {
        String[] routes = {
                "/provider/dashboard",
                "/provider/profile",
                "/provider/skills",
                "/provider/services",
                "/provider/availability",
                "/provider/offers",
                "/provider/applications",
                "/provider/bookings",
                "/provider/chat",
                "/provider/fees",
                "/provider/wallet",
                "/provider/reviews",
                "/provider/complaints",
                "/provider/posts"
        };

        for (String route : routes) {
            mvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(view().name("workspace/provider/page"));
        }

        Long requestId = serviceRequests.findAll().stream()
                .findFirst()
                .orElseThrow()
                .getId();

        mvc.perform(get("/provider/requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/provider/page"));

        mvc.perform(get("/provider/requests/{id}/apply", requestId))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/provider/page"));

        mvc.perform(get("/marketplace"))
                .andExpect(status().isOk());

        mvc.perform(get("/requests/browse"))
                .andExpect(status().isOk())
                .andExpect(view().name("requests/browse"));
    }

    @Test
    @WithMockUser(username = "provider1@skillrental.vn", roles = "PROVIDER")
    void providerPostActionsDoNotReturn5xx() throws Exception {
        User providerUser = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        ProviderProfile provider = providerProfiles.findByUser(providerUser).orElseThrow();
        Skill addableSkill = skills.findAll().stream()
                .filter(skill -> provider.getSkills().stream().noneMatch(current -> current.getId().equals(skill.getId())))
                .findFirst()
                .orElseThrow();
        ServiceRequest request = serviceRequests.findAll().stream().findFirst().orElseThrow();
        RentalOrder order = orders.findAll().stream()
                .filter(item -> item.getProvider() != null && item.getProvider().getId().equals(providerUser.getId()))
                .findFirst()
                .orElseThrow();
        Complaint complaint = complaints.findAll().stream()
                .filter(item -> item.getOrder() != null
                        && item.getOrder().getProvider() != null
                        && item.getOrder().getProvider().getId().equals(providerUser.getId()))
                .findFirst()
                .orElseThrow();
        Conversation conversation = conversations.findAll().stream()
                .filter(item -> item.getProvider() != null && item.getProvider().getId().equals(providerUser.getId()))
                .findFirst()
                .orElseThrow();

        mvc.perform(post("/provider/profile/save")
                        .with(csrf())
                        .param("headline", provider.getHeadline())
                        .param("hourlyRate", provider.getHourlyRate().toPlainString())
                        .param("introduction", provider.getIntroduction() == null ? "" : provider.getIntroduction())
                        .param("availability", provider.getAvailability() == null ? "" : provider.getAvailability()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/profile"));

        mvc.perform(post("/provider/mode")
                        .with(csrf())
                        .param("mode", "LIMITED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/availability"));

        mvc.perform(post("/provider/availability/save")
                        .with(csrf())
                        .param("leadTimeHours", "24")
                        .param("maxConcurrent", "3")
                        .param("note", "Remote"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/availability"));

        mvc.perform(post("/provider/availability/vacation")
                        .with(csrf())
                        .param("from", "2026-10-10")
                        .param("to", "2026-10-12")
                        .param("message", "Nghỉ ngắn ngày"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/availability"));

        mvc.perform(post("/provider/skills/{id}/add", addableSkill.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/skills"));

        mvc.perform(post("/provider/skills/{id}/remove", addableSkill.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/skills"));

        mvc.perform(post("/provider/services/new")
                        .with(csrf())
                        .param("name", "Gói test")
                        .param("category", "Kiểm thử")
                        .param("description", "Gói dịch vụ dùng trong kiểm thử hồi quy.")
                        .param("pricingType", "FIXED")
                        .param("price", "1500000")
                        .param("durationMinutes", "60"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/services"));

        mvc.perform(post("/provider/requests/{id}/apply", request.getId())
                        .with(csrf())
                        .param("proposedPrice", "3200000")
                        .param("durationDays", "7")
                        .param("proposal", "Đề xuất test"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/provider/orders/{id}/start-confirmation", order.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/bookings"));

        mvc.perform(post("/provider/orders/{id}/complete", order.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/bookings"));

        mvc.perform(post("/provider/complaints/{id}/evidence", complaint.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/complaints"));

        mvc.perform(post("/provider/complaints/{id}/refund", complaint.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/complaints"));

        mvc.perform(post("/provider/wallet/withdraw")
                        .with(csrf())
                        .param("amount", "2000000")
                        .param("bankName", "Test Bank")
                        .param("accountNumber", "TEST-ACCOUNT-001")
                        .param("accountHolder", "Test Provider"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/wallet"));

        mvc.perform(post("/provider/posts")
                        .with(csrf())
                        .param("title", "Bài viết test")
                        .param("category", "Lập trình Web")
                        .param("content", "Nội dung được backend nhận và lưu trong WorkspaceState."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/posts"));

        mvc.perform(post("/provider/fees/pay")
                        .with(csrf())
                        .param("method", "VNPay QR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/fees"));

        mvc.perform(post("/provider/chat/{id}/send", conversation.getId())
                        .with(csrf())
                        .param("content", "Tin nhắn smoke test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/chat"));
    }

    @Test
    @WithMockUser(username = "provider1@skillrental.vn", roles = "PROVIDER")
    void providerCanRespondToOwnReview() throws Exception {
        User providerUser = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        Review review = reviews.findByProviderOrderByIdDesc(providerUser).stream()
                .findFirst()
                .orElseThrow();

        mvc.perform(post("/provider/reviews/{id}/respond", review.getId())
                        .with(csrf())
                        .param("response", "Cảm ơn bạn đã phản hồi."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/reviews"));
    }

    @Test
    @WithMockUser(username = "provider1@skillrental.vn", roles = "PROVIDER")
    void providerCanDeclinePendingBookingAndReleaseFullEscrow() throws Exception {
        User providerUser = users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow();
        RentalOrder pending = orders.findAll().stream()
                .filter(order -> order.getProvider() != null
                        && order.getProvider().getId().equals(providerUser.getId())
                        && order.getStatus() == OrderStatus.PENDING_PROVIDER_ACCEPTANCE)
                .findFirst().orElseThrow();
        UserWallet before = wallets.findByUser(pending.getCustomer()).orElseThrow();
        BigDecimal availableBefore = before.getBalance();
        BigDecimal lockedBefore = before.getLockedAmount();
        BigDecimal amount = pending.getLockedAmount();

        mvc.perform(post("/provider/orders/{id}/decline", pending.getId())
                        .with(csrf())
                        .param("reason", "Provider không thể nhận lịch này."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/bookings"));

        RentalOrder after = orders.findById(pending.getId()).orElseThrow();
        UserWallet walletAfter = wallets.findByUser(pending.getCustomer()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, after.getStatus());
        assertEquals(BigDecimal.ZERO.setScale(2), after.getLockedAmount().setScale(2));
        assertEquals(availableBefore.add(amount).setScale(2), walletAfter.getBalance().setScale(2));
        assertEquals(lockedBefore.subtract(amount).setScale(2), walletAfter.getLockedAmount().setScale(2));
    }

    @Test
    @WithMockUser(username = "admin@skillrental.vn", roles = "ADMIN")
    void allAdminGetRoutesRenderWithoutServerError() throws Exception {
        workspaceState.reset();

        String[] routes = {
                "/admin/dashboard",
                "/admin/users",
                "/admin/locked",
                "/admin/unlock",
                "/admin/providers",
                "/admin/pending-providers",
                "/admin/skills",
                "/admin/services",
                "/admin/pending-services",
                "/admin/price-requests",
                "/admin/blocked-services",
                "/admin/orders",
                "/admin/reviews",
                "/admin/complaints",
                "/admin/reports",
                "/admin/statistics",
                "/admin/finance",
                "/admin/packages"
        };

        for (String route : routes) {
            mvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(view().name("workspace/admin/page"));
        }

        mvc.perform(get("/admin/statistics")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-18"))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/admin/page"))
                .andExpect(model().attributeExists("timePoints", "statusSlices", "fromDate", "toDate"));

        mvc.perform(get("/admin/dashboard")
                        .param("from", "2026-09-20")
                        .param("to", "2026-10-18"))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/admin/page"))
                .andExpect(model().attributeExists("financePoints", "commissionPoints", "fromDate", "toDate", "commission"));

        mvc.perform(get("/admin/finance")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-18"))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/admin/page"))
                .andExpect(model().attributeExists("financePoints", "commissionPoints", "fromDate", "toDate", "commission"));

        mvc.perform(get("/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("reportRows"));

        Long providerId = providerProfiles.findAll().stream()
                .findFirst()
                .orElseThrow()
                .getId();

        mvc.perform(get("/admin/providers/{id}", providerId))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/admin/page"));

        mvc.perform(get("/marketplace"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("popularServices"));
    }

    @Test
    @WithMockUser(username = "customer@skillrental.vn", roles = "CUSTOMER")
    void customerWalletShowsAvailableLockedAndOriginalAmounts() throws Exception {
        mvc.perform(get("/customer/wallet"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/wallet"))
                .andExpect(model().attributeExists("wallet", "transactions", "demoTopupEnabled"));
    }
}
