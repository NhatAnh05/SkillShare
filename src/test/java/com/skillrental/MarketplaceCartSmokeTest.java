package com.skillrental;

import com.skillrental.entity.ServiceStatus;
import com.skillrental.repository.OfferedServiceRepository;
import com.skillrental.repository.CartRepository;
import com.skillrental.repository.CartItemRepository;
import com.skillrental.entity.CartStatus;
import com.skillrental.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

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
    @Autowired CartItemRepository items;
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
        var existingItem = carts.findFirstByCustomerAndStatusOrderByUpdatedAtDesc(customer, CartStatus.ACTIVE)
                .flatMap(cart -> items.findByCartAndServiceId(cart, service.getId()));
        existingItem.ifPresent(items::delete);
        items.flush();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/cart/items").session(session).with(csrf())
                        .param("serviceId", service.getId().toString()).param("requestToken", "first-add"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cart"));
        var cart = carts.findFirstByCustomerAndStatusOrderByUpdatedAtDesc(customer, CartStatus.ACTIVE).orElseThrow();
        var item = items.findByCartAndServiceId(cart, service.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1, item.getQuantity());
        mvc.perform(post("/cart/items").session(session).with(csrf())
                        .param("serviceId", service.getId().toString()).param("requestToken", "first-add"))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertEquals(1,
                items.findByCartAndServiceId(cart, service.getId()).orElseThrow().getQuantity());
        Long itemId = item.getId();
        mvc.perform(post("/cart/items/{itemId}/remove", itemId).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cart"));
        org.junit.jupiter.api.Assertions.assertTrue(items.findByCartAndServiceId(cart, service.getId()).isEmpty());
        mvc.perform(post("/cart/items").session(session).with(csrf())
                        .param("serviceId", service.getId().toString()).param("requestToken", "after-remove"))
                .andExpect(status().is3xxRedirection());
        item = items.findByCartAndServiceId(cart, service.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1, item.getQuantity());
        mvc.perform(post("/cart/items").session(session).with(csrf())
                        .param("serviceId", service.getId().toString()).param("quantity", "1")
                        .param("requestToken", "intentional-add"))
                .andExpect(status().is3xxRedirection());
        item = items.findByCartAndServiceId(cart, service.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(2, item.getQuantity());
        mvc.perform(post("/cart/items/{itemId}/quantity", item.getId()).with(csrf()).param("quantity", "2"))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertEquals(2,
                items.findByCartAndServiceId(cart, service.getId()).orElseThrow().getQuantity());
    }
}
