package com.skillrental.service;

import com.skillrental.entity.User;
import com.skillrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor public class CurrentUserService {
    private final UserRepository users;
    public User require(Authentication a) {
        if(a==null||!a.isAuthenticated()||a instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"Phiên đăng nhập đã kết thúc.");
        var user=users.findByEmailIgnoreCase(a.getName()).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));
        if(user.getAccountStatus()!=com.skillrental.entity.AccountStatus.ACTIVE||!Boolean.TRUE.equals(user.getEmailVerified()))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Tài khoản không còn hoạt động hoặc chưa xác minh.");
        return user;
    }
}
