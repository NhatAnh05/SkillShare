package com.skillrental;

import com.skillrental.entity.OrderStatus;
import com.skillrental.entity.RentalOrder;
import com.skillrental.entity.RentalOrderStatusHistory;
import com.skillrental.entity.User;
import com.skillrental.repository.RentalOrderRepository;
import com.skillrental.repository.RentalOrderStatusHistoryRepository;
import com.skillrental.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminOrderManagementTest {
    @Autowired private MockMvc mvc;
    @Autowired private RentalOrderRepository orders;
    @Autowired private RentalOrderStatusHistoryRepository history;
    @Autowired private UserRepository users;

    @Test
    @WithMockUser(username = "admin@skillrental.vn", roles = "ADMIN")
    void adminReviewIsPersistedWithActorAndTimeWithoutChangingOrderOrMoney() throws Exception {
        RentalOrder order = orders.findAll().stream().findFirst().orElseThrow();
        OrderStatus statusBefore = order.getStatus();
        var totalBefore = order.getTotal();
        var lockedBefore = order.getLockedAmount();
        int historyBefore = history.findByOrderIdOrderByCreatedAtAsc(order.getId()).size();
        User admin = users.findByEmailIgnoreCase("admin@skillrental.vn").orElseThrow();

        mvc.perform(post("/admin/orders/{id}/intervene", order.getId()).with(csrf())
                        .param("note", "Đã đối chiếu hồ sơ với các bên liên quan."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders"))
                .andExpect(flash().attributeExists("success"));

        RentalOrder persistedOrder = orders.findById(order.getId()).orElseThrow();
        assertEquals(statusBefore, persistedOrder.getStatus());
        assertEquals(totalBefore, persistedOrder.getTotal());
        assertEquals(lockedBefore, persistedOrder.getLockedAmount());

        var persistedHistory = history.findByOrderIdOrderByCreatedAtAsc(order.getId());
        assertEquals(historyBefore + 1, persistedHistory.size());
        RentalOrderStatusHistory review = persistedHistory.get(persistedHistory.size() - 1);
        assertEquals(statusBefore.name(), review.getPreviousStatus());
        assertEquals(statusBefore.name(), review.getNewStatus());
        assertEquals(admin.getId(), review.getChangedBy().getId());
        assertEquals("Đã đối chiếu hồ sơ với các bên liên quan.", review.getNote());
        org.junit.jupiter.api.Assertions.assertNotNull(review.getCreatedAt());

        mvc.perform(get("/admin/orders"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model()
                        .attributeExists("adminOrderRows"))
                .andExpect(content().string(containsString("Đã đối chiếu hồ sơ với các bên liên quan.")));
    }

    @Test
    @WithMockUser(username = "admin@skillrental.vn", roles = "ADMIN")
    void missingOrderAndBlankReviewAreReportedWithoutWritingHistory() throws Exception {
        RentalOrder order = orders.findAll().stream().findFirst().orElseThrow();
        int historyBefore = history.findByOrderIdOrderByCreatedAtAsc(order.getId()).size();

        mvc.perform(post("/admin/orders/{id}/intervene", 999999L).with(csrf())
                        .param("note", "Kiểm tra ID không tồn tại"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders"))
                .andExpect(flash().attributeExists("error"));

        mvc.perform(post("/admin/orders/{id}/intervene", order.getId()).with(csrf())
                        .param("note", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertEquals(historyBefore, history.findByOrderIdOrderByCreatedAtAsc(order.getId()).size());
    }

    @Test
    @WithMockUser(username = "admin@skillrental.vn", roles = "ADMIN")
    void identicalRepeatedReviewIsRejectedInsteadOfDuplicated() throws Exception {
        RentalOrder order = orders.findAll().stream().findFirst().orElseThrow();
        int historyBefore = history.findByOrderIdOrderByCreatedAtAsc(order.getId()).size();
        String note = "Đã xác minh thông tin đơn.";

        mvc.perform(post("/admin/orders/{id}/intervene", order.getId()).with(csrf()).param("note", note))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));
        mvc.perform(post("/admin/orders/{id}/intervene", order.getId()).with(csrf()).param("note", note))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertEquals(historyBefore + 1, history.findByOrderIdOrderByCreatedAtAsc(order.getId()).size());
    }

    @Test
    @WithMockUser(username = "customer@skillrental.vn", roles = "CUSTOMER")
    void nonAdminCannotRecordOrderReview() throws Exception {
        RentalOrder order = orders.findAll().stream().findFirst().orElseThrow();
        int historyBefore = history.findByOrderIdOrderByCreatedAtAsc(order.getId()).size();

        mvc.perform(post("/admin/orders/{id}/intervene", order.getId()).with(csrf())
                        .param("note", "Không được ghi nhận"))
                .andExpect(status().isForbidden());

        assertEquals(historyBefore, history.findByOrderIdOrderByCreatedAtAsc(order.getId()).size());
    }
}
