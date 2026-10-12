package com.skillrental.service;

import com.skillrental.dto.RegisterRequest;
import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final CustomerProfileRepository customers;
    private final PasswordEncoder encoder;
    @Transactional public User register(RegisterRequest f) {
        if(!f.getPassword().equals(f.getConfirmPassword())) throw new IllegalArgumentException("Mật khẩu xác nhận không khớp");
        if(users.existsByEmailIgnoreCase(f.getEmail())) throw new IllegalArgumentException("Email đã được sử dụng");
        var u=users.save(User.builder().fullName(f.getFullName()).email(f.getEmail().toLowerCase()).password(encoder.encode(f.getPassword())).role(Role.CUSTOMER).avatarUrl("https://i.pravatar.cc/150?img=33").bio("Customer của Skill Rental").build());
        customers.save(CustomerProfile.builder().user(u).location("TP. Hồ Chí Minh").preferredSkills("Java, UI/UX, Marketing").build());
        return u;
    }
    @Transactional public String createResetToken(String email) {
        var u=users.findByEmailIgnoreCase(email).orElseThrow(()->new IllegalArgumentException("Không tìm thấy email"));
        var t=UUID.randomUUID().toString();
        u.setResetToken(t);
        users.save(u);
        return t;
    }
    @Transactional public void reset(String token,String p,String c) {
        if(p==null||p.length()<6) throw new IllegalArgumentException("Mật khẩu tối thiểu 6 ký tự");
        if(!p.equals(c)) throw new IllegalArgumentException("Mật khẩu xác nhận không khớp");
        var u=users.findByResetToken(token).orElseThrow(()->new IllegalArgumentException("Token không hợp lệ"));
        u.setPassword(encoder.encode(p));
        u.setResetToken(null);
        users.save(u);
    }
}
