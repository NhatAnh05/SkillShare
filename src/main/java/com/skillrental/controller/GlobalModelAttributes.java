package com.skillrental.controller;

import com.skillrental.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice @RequiredArgsConstructor public class GlobalModelAttributes {
    private final CurrentUserService current;
    @ModelAttribute("currentUser") public Object user(Authentication a) {
        try {
            return current.require(a);
        } catch(Exception e) {
            return null;
        }
    }
}
