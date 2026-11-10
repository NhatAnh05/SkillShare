package com.skillrental;

import com.skillrental.entity.Escrow;
import com.skillrental.entity.EscrowStatus;
import com.skillrental.entity.OrderStatus;
import com.skillrental.entity.ProviderProfile;
import com.skillrental.entity.RentalOrder;
import com.skillrental.entity.Role;
import com.skillrental.entity.User;
import com.skillrental.repository.ProviderProfileRepository;
import com.skillrental.repository.RentalOrderRepository;
import com.skillrental.repository.UserRepository;
import com.skillrental.service.EscrowService;
import com.skillrental.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EscrowFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private ProviderProfileRepository providerProfiles;
    @Autowired private RentalOrderRepository orders;
    @Autowired private WalletService walletService;
    @Autowired private EscrowService escrowService;

    @Test
    @WithMockUser(username = "customer@skillrental.vn", roles = "CUSTOMER")
    void bookingHoldsFundsThenBothConfirmAdvanceAndOtpFinalizesPayment() throws Exception {
        User customer = users.findByEmailIgnoreCase("customer@skillrental.vn").orElseThrow();
        ProviderProfile provider = providerProfiles.findByUser(
                users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow()).orElseThrow();
        User providerUser = provider.getUser();
        assertThat(customer.getRole()).isEqualTo(Role.CUSTOMER);

        walletService.deposit(customer, new BigDecimal("1000000"), "TEST");
        BigDecimal heldBeforeBooking = walletService.held(customer);

        MvcResult booking = mvc.perform(post("/booking/provider/{id}", provider.getId())
                        .with(csrf())
                        .param("serviceName", "Tư vấn kiểm thử escrow")
                        .param("bookingDate", java.time.LocalDate.now().plusDays(2).toString())
                        .param("timeSlot", "10:00")
                        .param("durationHours", "2")
                        .param("directBooking", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/booking/complete/*"))
                .andReturn();

        Long orderId = Long.parseLong(booking.getResponse().getRedirectedUrl().replace("/booking/complete/", ""));
        RentalOrder order = orders.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ESCROW_HELD);

        Escrow escrow = escrowService.requireEscrow(order);
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.HELD);
        assertThat(escrow.getTotalAmount()).isEqualByComparingTo("500000");
        assertThat(walletService.held(customer)).isEqualByComparingTo(heldBeforeBooking.add(new BigDecimal("500000")));

        mvc.perform(get("/wallet"))
                .andExpect(status().isOk())
                .andExpect(view().name("wallet/index"));
        mvc.perform(get("/booking/complete/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/complete"));
        mvc.perform(get("/orders/{id}/progress", orderId))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/progress"));

        mvc.perform(post("/orders/{id}/start-confirm", orderId).with(csrf()))
                .andExpect(status().is3xxRedirection());
        escrowService.confirmStart(order, providerUser);

        Escrow started = escrowService.requireEscrow(order);
        assertThat(started.getStatus()).isEqualTo(EscrowStatus.STARTED);
        assertThat(started.getAdvanceReleased()).isTrue();
        assertThat(started.getReleasedAmount()).isEqualByComparingTo("166666.67");
        assertThat(walletService.available(providerUser)).isGreaterThanOrEqualTo(new BigDecimal("166666.67"));

        escrowService.providerComplete(order, providerUser);
        assertThat(escrowService.requireEscrow(order).getStatus()).isEqualTo(EscrowStatus.AWAITING_CONFIRMATION);

        MvcResult accept = mvc.perform(post("/orders/{id}/accept", orderId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/orders/*/confirm-payment"))
                .andReturn();
        String devOtp = (String) accept.getFlashMap().get("devOtp");
        assertThat(devOtp).isNotBlank();

        mvc.perform(get("/orders/{id}/confirm-payment", orderId))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/confirm-payment"))
                .andExpect(model().attributeExists("remaining"));

        mvc.perform(post("/orders/{id}/confirm-payment", orderId)
                        .with(csrf())
                        .param("code", devOtp))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"));

        RentalOrder completed = orders.findById(orderId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        Escrow released = escrowService.requireEscrow(completed);
        assertThat(released.getStatus()).isEqualTo(EscrowStatus.RELEASED);
        assertThat(released.getReleasedAmount()).isEqualByComparingTo("500000");
        assertThat(walletService.held(customer)).isEqualByComparingTo(heldBeforeBooking);

        mvc.perform(get("/provider/bookings")
                        .with(user("provider1@skillrental.vn").roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/provider/page"));
    }

    @Test
    @WithMockUser(username = "customer@skillrental.vn", roles = "CUSTOMER")
    void otpIsInvalidatedAfterFiveWrongAttemptsThenResendAllowsFinalPayment() throws Exception {
        User customer = users.findByEmailIgnoreCase("customer@skillrental.vn").orElseThrow();
        ProviderProfile provider = providerProfiles.findByUser(
                users.findByEmailIgnoreCase("provider1@skillrental.vn").orElseThrow()).orElseThrow();
        User providerUser = provider.getUser();

        walletService.deposit(customer, new BigDecimal("1000000"), "TEST");

        MvcResult booking = mvc.perform(post("/booking/provider/{id}", provider.getId())
                        .with(csrf())
                        .param("serviceName", "Kiểm thử khóa OTP sau 5 lần sai")
                        .param("bookingDate", java.time.LocalDate.now().plusDays(2).toString())
                        .param("timeSlot", "12:00")
                        .param("durationHours", "2")
                        .param("directBooking", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Long orderId = Long.parseLong(booking.getResponse().getRedirectedUrl().replace("/booking/complete/", ""));
        RentalOrder order = orders.findById(orderId).orElseThrow();

        escrowService.confirmStart(order, customer);
        escrowService.confirmStart(order, providerUser);
        escrowService.providerComplete(order, providerUser);
        assertThat(escrowService.requireEscrow(order).getStatus()).isEqualTo(EscrowStatus.AWAITING_CONFIRMATION);

        MvcResult accept = mvc.perform(post("/orders/{id}/accept", orderId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String devOtp = (String) accept.getFlashMap().get("devOtp");
        assertThat(devOtp).isNotBlank();

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/orders/{id}/confirm-payment", orderId)
                            .with(csrf())
                            .param("code", "000000"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("orders/confirm-payment"));
        }

        mvc.perform(post("/orders/{id}/confirm-payment", orderId)
                        .with(csrf())
                        .param("code", devOtp))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/confirm-payment"));
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.AWAITING_CONFIRMATION);

        MvcResult resend = mvc.perform(post("/orders/{id}/resend-otp", orderId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String freshOtp = (String) resend.getFlashMap().get("devOtp");
        assertThat(freshOtp).isNotBlank();

        mvc.perform(post("/orders/{id}/confirm-payment", orderId)
                        .with(csrf())
                        .param("code", freshOtp))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"));

        RentalOrder completed = orders.findById(orderId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(escrowService.requireEscrow(completed).getStatus()).isEqualTo(EscrowStatus.RELEASED);
    }
}
