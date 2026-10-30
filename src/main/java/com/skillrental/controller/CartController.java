package com.skillrental.controller;

import com.skillrental.entity.*;
import com.skillrental.repository.CartItemRepository;
import com.skillrental.repository.CartRepository;
import com.skillrental.repository.ProviderProfileRepository;
import com.skillrental.service.ServiceCatalogService;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.ServicePriceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpSession;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class CartController {
    private final CurrentUserService currentUser;
    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProviderProfileRepository providers;
    private final ServiceCatalogService services;
    private final ServicePriceCalculator priceCalculator;

    @GetMapping("/services")
    public String services(Model model) {
        var catalog = services.availablePublishedServices();
        model.addAttribute("services", catalog);
        Map<Long, ServicePriceCalculator.PriceBreakdown> prices = new HashMap<>();
        catalog.forEach(service -> prices.put(service.getId(), priceCalculator.calculate(service, 1,
                ServicePriceCalculator.DEFAULT_DURATION_HOURS, false)));
        model.addAttribute("prices", prices);
        model.addAttribute("addRequestToken", UUID.randomUUID().toString());
        return "marketplace/services";
    }

    @GetMapping("/cart")
    public String cart(Authentication auth, Model model) {
        Cart cart = activeCart(requireCustomer(auth));
        model.addAttribute("cart", cart);
        model.addAttribute("items", cart.getItems());
        Map<Long, ServicePriceCalculator.PriceBreakdown> itemPrices = new HashMap<>();
        cart.getItems().forEach(item -> itemPrices.put(item.getId(), priceCalculator.calculate(
                item.getService(), item.getQuantity(), ServicePriceCalculator.DEFAULT_DURATION_HOURS, false)));
        java.util.Map<Long, BigDecimal> lineTotals = new java.util.HashMap<>();
        cart.getItems().forEach(item -> lineTotals.put(item.getId(), itemPrices.get(item.getId()).total()));
        model.addAttribute("lineTotals", lineTotals);
        model.addAttribute("itemPrices", itemPrices);
        model.addAttribute("priceChanged", cart.getItems().stream().collect(
                java.util.stream.Collectors.toMap(CartItem::getId,
                        item -> item.getUnitPriceSnapshot().compareTo(item.getService().getPrice()) != 0)));
        model.addAttribute("total", lineTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        return "customer/cart";
    }

    @PostMapping("/cart/items")
    @Transactional
    public synchronized String add(@RequestParam("serviceId") Long serviceId,
                      @RequestParam(value = "quantity", defaultValue = "1") Integer quantity,
                      @RequestParam("requestToken") String requestToken, Authentication auth,
                      HttpSession session, RedirectAttributes flash) {
        requireCustomer(auth);
        if (quantity == null || quantity < 1 || quantity > 99) {
            flash.addFlashAttribute("error", "Số lượng phải từ 1 đến 99.");
            return "redirect:/services";
        }
        if (requestToken.isBlank() || requestToken.length() > 100) {
            flash.addFlashAttribute("error", "Mã yêu cầu thêm dịch vụ không hợp lệ. Hãy tải lại trang.");
            return "redirect:/services";
        }
        if (alreadyProcessed(session, requestToken)) {
            flash.addFlashAttribute("info", "Yêu cầu thêm dịch vụ này đã được xử lý.");
            return "redirect:/cart";
        }
        ServiceCatalog service;
        try {
            service = services.requireAvailablePublishedService(serviceId);
        } catch (ServiceCatalogService.ServiceUnavailableException e) {
            flash.addFlashAttribute("error", "Dịch vụ hiện không được mở bán.");
            return "redirect:/services";
        }
        User customer = currentUser.require(auth);
        Cart cart = activeCartForUpdate(customer);
        CartItem item = items.findByCartAndServiceIdForUpdate(cart, serviceId).orElse(null);
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
        }
        carts.save(cart);
        rememberProcessed(session, requestToken);
        flash.addFlashAttribute("success", "Đã thêm dịch vụ vào danh sách đã chọn.");
        return "redirect:/cart";
    }

    @PostMapping("/cart/items/{itemId}/quantity")
    @Transactional
    public String quantity(@PathVariable("itemId") Long itemId, @RequestParam("quantity") Integer quantity,
                           Authentication auth,
                           RedirectAttributes flash) {
        Cart cart = activeCart(requireCustomer(auth));
        CartItem item = items.findByIdAndCart(itemId, cart)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dịch vụ không nằm trong giỏ của tài khoản hiện tại."));
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
    public String remove(@PathVariable("itemId") Long itemId, Authentication auth, RedirectAttributes flash) {
        Cart cart = activeCart(requireCustomer(auth));
        CartItem item = items.findByIdAndCart(itemId, cart)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dịch vụ không nằm trong giỏ của tài khoản hiện tại."));
        items.delete(item);
        cart.getItems().remove(item);
        items.flush();
        cart.setUpdatedAt(java.time.LocalDateTime.now());
        flash.addFlashAttribute("success", "Đã xóa dịch vụ khỏi danh sách đã chọn.");
        return "redirect:/cart";
    }

    @GetMapping("/cart/items/{itemId}/booking")
    public String bookCartItem(@PathVariable("itemId") Long itemId, Authentication auth, RedirectAttributes flash) {
        User customer = requireCustomer(auth);
        Cart cart = activeCart(customer);
        CartItem item = items.findByIdAndCart(itemId, cart)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dịch vụ trong giỏ hàng."));
        try {
            ServiceCatalog service = services.requireAvailablePublishedService(item.getService().getId());
            ProviderProfile provider = providers.findByUser(service.getProvider())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ Provider."));
            return "redirect:/booking/provider/" + provider.getId()
                    + "?serviceId=" + service.getId() + "&cartItemId=" + item.getId();
        } catch (ServiceCatalogService.ServiceUnavailableException e) {
            flash.addFlashAttribute("error", "Dịch vụ hiện không còn được mở bán.");
            return "redirect:/cart";
        }
    }

    private Cart activeCart(User customer) {
        return carts.findFirstByCustomerAndStatusOrderByUpdatedAtDesc(customer, CartStatus.ACTIVE)
                .orElseGet(() -> carts.save(Cart.builder().customer(customer).status(CartStatus.ACTIVE).build()));
    }

    private Cart activeCartForUpdate(User customer) {
        return carts.findActiveForUpdate(customer, CartStatus.ACTIVE).stream().findFirst()
                .orElseGet(() -> carts.saveAndFlush(
                        Cart.builder().customer(customer).status(CartStatus.ACTIVE).build()));
    }

    @SuppressWarnings("unchecked")
    private boolean alreadyProcessed(HttpSession session, String token) {
        Map<String, Long> processed = (Map<String, Long>) session.getAttribute("processedCartAddTokens");
        if (processed == null) return false;
        long now = System.currentTimeMillis();
        processed.entrySet().removeIf(entry -> now - entry.getValue() > 30 * 60 * 1000);
        return processed.containsKey(token);
    }

    @SuppressWarnings("unchecked")
    private void rememberProcessed(HttpSession session, String token) {
        if (token == null || token.isBlank()) return;
        Map<String, Long> processed = (Map<String, Long>) session.getAttribute("processedCartAddTokens");
        if (processed == null) processed = new HashMap<>();
        long now = System.currentTimeMillis();
        processed.entrySet().removeIf(entry -> now - entry.getValue() > 30 * 60 * 1000);
        if (processed.size() >= 500) {
            String oldest = processed.entrySet().stream().min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            if (oldest != null) processed.remove(oldest);
        }
        processed.put(token, now);
        session.setAttribute("processedCartAddTokens", processed);
    }

    private User requireCustomer(Authentication authentication) {
        User user = currentUser.require(authentication);
        if (user.getRole() != Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Customer mới được quản lý giỏ hàng.");
        }
        return user;
    }
}
