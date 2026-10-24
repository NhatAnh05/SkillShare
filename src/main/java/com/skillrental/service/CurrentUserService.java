package com.skillrental.service;

import com.skillrental.entity.User;
import com.skillrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor public class CurrentUserService {
    private final UserRepository users;
    public User require(Authentication a) {
        if(a==null||!a.isAuthenticated()) throw new IllegalStateException("Chưa đăng nhập");
        return users.findByEmailIgnoreCase(a.getName()).orElseThrow();
    }
}
