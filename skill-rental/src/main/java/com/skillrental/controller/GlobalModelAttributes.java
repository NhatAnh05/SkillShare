package com.skillrental.controller;

import com.skillrental.service.CurrentUserService;
import com.skillrental.entity.CartStatus;
import com.skillrental.entity.Role;
import com.skillrental.repository.CartItemRepository;
import com.skillrental.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice @RequiredArgsConstructor public class GlobalModelAttributes {
    private final CurrentUserService current;
    private final CartItemRepository cartItems;
    private final UserRepository users;
    @ModelAttribute("currentUser") public Object user(Authentication a) {
        try {
            return current.require(a);
        } catch(Exception e) {
            return null;
        }
    }

    @ModelAttribute("cartItemCount")
    public int cartItemCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) return 0;
        return users.findByEmailIgnoreCase(authentication.getName())
                .filter(user -> user.getRole() == Role.CUSTOMER)
                .map(user -> cartItems.sumQuantityByCustomerAndCartStatus(user, CartStatus.ACTIVE))
                .map(Long::intValue)
                .orElse(0);
    }

    @ModelAttribute("requestURI")
    public String requestURI(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
