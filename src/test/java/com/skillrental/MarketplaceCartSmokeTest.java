package com.skillrental;

import com.skillrental.entity.ServiceStatus;
import com.skillrental.repository.OfferedServiceRepository;
import com.skillrental.repository.CartRepository;
import com.skillrental.entity.CartStatus;
import com.skillrental.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MarketplaceCartSmokeTest {
    @Autowired MockMvc mvc;
    @Autowired OfferedServiceRepository services;
    @Autowired CartRepository carts;
    @Autowired UserRepository users;

    @Test
    @WithMockUser(username = "customer@skillrental.vn", roles = "CUSTOMER")
    void servicesAndCartPagesRender() throws Exception {
        mvc.perform(get("/services")).andExpect(status().isOk()).andExpect(view().name("marketplace/services"));
        mvc.perform(get("/cart")).andExpect(status().isOk()).andExpect(view().name("customer/cart"));
    }

    @Test
    @WithMockUser(username = "customer@skillrental.vn", roles = "CUSTOMER")
    void customerCanAddAndRemovePublishedServiceFromCart() throws Exception {
        var service = services.findByStatusOrderByRatingDesc(ServiceStatus.PUBLISHED).stream().findFirst().orElseThrow();
        var customer = users.findByEmailIgnoreCase("customer@skillrental.vn").orElseThrow();
        mvc.perform(post("/cart/items").with(csrf()).param("serviceId", service.getId().toString()).param("quantity", "2"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cart"));
        var cart = carts.findFirstByCustomerAndStatusOrderByUpdatedAtDesc(customer, CartStatus.ACTIVE).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1, cart.getItems().size());
        org.junit.jupiter.api.Assertions.assertEquals(2, cart.getItems().get(0).getQuantity());
        Long itemId = cart.getItems().get(0).getId();
        mvc.perform(post("/cart/items/{itemId}/remove", itemId).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cart"));
    }
}
