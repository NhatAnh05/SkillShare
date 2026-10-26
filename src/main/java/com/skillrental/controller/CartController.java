package com.skillrental.controller;

import com.skillrental.entity.*;
import com.skillrental.repository.CartItemRepository;
import com.skillrental.repository.CartRepository;
import com.skillrental.service.ServiceCatalogService;
import com.skillrental.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class CartController {
    private final CurrentUserService currentUser;
    private final CartRepository carts;
    private final CartItemRepository items;
    private final ServiceCatalogService services;

    @GetMapping("/services")
    public String services(Model model) {
        model.addAttribute("services", services.availablePublishedServices());
        return "marketplace/services";
    }

    @GetMapping("/cart")
    public String cart(Authentication auth, Model model) {
        Cart cart = activeCart(currentUser.require(auth));
        model.addAttribute("cart", cart);
        model.addAttribute("items", cart.getItems());
        java.util.Map<Long, BigDecimal> lineTotals = new java.util.HashMap<>();
        cart.getItems().forEach(item -> lineTotals.put(item.getId(),
                item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()))));
        model.addAttribute("lineTotals", lineTotals);
        model.addAttribute("total", lineTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        return "customer/cart";
    }

    @PostMapping("/cart/items")
    @Transactional
    public String add(@RequestParam Long serviceId, @RequestParam(defaultValue = "1") Integer quantity,
                      Authentication auth, RedirectAttributes flash) {
        if (quantity == null || quantity < 1 || quantity > 99) {
            flash.addFlashAttribute("error", "Số lượng phải từ 1 đến 99.");
            return "redirect:/services";
        }
        ServiceCatalog service;
        try {
            service = services.requireAvailablePublishedService(serviceId);
        } catch (ServiceCatalogService.ServiceUnavailableException e) {
            flash.addFlashAttribute("error", "Dịch vụ hiện không được mở bán.");
            return "redirect:/services";
        }
        Cart cart = activeCart(currentUser.require(auth));
        CartItem item = items.findByCartAndServiceId(cart, serviceId).orElse(null);
        if (item == null) {
            item = CartItem.builder().cart(cart).service(service).quantity(quantity)
                    .unitPriceSnapshot(service.getPrice()).build();
            cart.getItems().add(item);
        } else {
            int next = item.getQuantity() + quantity;
            if (next > 99) {
                flash.addFlashAttribute("error", "Tổng số lượng của một dịch vụ không được vượt quá 99.");
                return "redirect:/cart";
            }
            item.setQuantity(next);
            item.setUnitPriceSnapshot(service.getPrice());
        }
        carts.save(cart);
        flash.addFlashAttribute("success", "Đã thêm dịch vụ vào danh sách đã chọn.");
        return "redirect:/cart";
    }

    @PostMapping("/cart/items/{itemId}/quantity")
    @Transactional
    public String quantity(@PathVariable Long itemId, @RequestParam Integer quantity, Authentication auth,
                           RedirectAttributes flash) {
        Cart cart = activeCart(currentUser.require(auth));
        CartItem item = ownedItem(cart, itemId);
        if (quantity == null || quantity < 1 || quantity > 99) {
            flash.addFlashAttribute("error", "Số lượng phải từ 1 đến 99.");
            return "redirect:/cart";
        }
        item.setQuantity(quantity);
        carts.save(cart);
        flash.addFlashAttribute("success", "Đã cập nhật số lượng.");
        return "redirect:/cart";
    }

    @PostMapping("/cart/items/{itemId}/remove")
    @Transactional
    public String remove(@PathVariable Long itemId, Authentication auth, RedirectAttributes flash) {
        Cart cart = activeCart(currentUser.require(auth));
        CartItem item = ownedItem(cart, itemId);
        cart.getItems().remove(item);
        carts.save(cart);
        flash.addFlashAttribute("success", "Đã xóa dịch vụ khỏi danh sách đã chọn.");
        return "redirect:/cart";
    }

    private Cart activeCart(User customer) {
        return carts.findFirstByCustomerAndStatusOrderByUpdatedAtDesc(customer, CartStatus.ACTIVE)
                .orElseGet(() -> carts.save(Cart.builder().customer(customer).status(CartStatus.ACTIVE).build()));
    }

    private CartItem ownedItem(Cart cart, Long itemId) {
        return cart.getItems().stream().filter(item -> item.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Dịch vụ không nằm trong giỏ của tài khoản hiện tại."));
    }
}
