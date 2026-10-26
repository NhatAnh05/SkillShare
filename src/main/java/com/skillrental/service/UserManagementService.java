package com.skillrental.service;

import com.skillrental.entity.AccountStatus;
import com.skillrental.entity.Role;
import com.skillrental.entity.User;
import com.skillrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserManagementService {
    private final UserRepository users;

    @Transactional
    public void lock(Long userId) {
        User user = findUser(userId);
        requireNonAdmin(user);
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountManagementException("Chỉ có thể khóa tài khoản đang hoạt động.");
        }
        user.setAccountStatus(AccountStatus.LOCKED);
        users.save(user);
    }

    @Transactional
    public void unlock(Long userId) {
        User user = findUser(userId);
        requireNonAdmin(user);
        if (user.getAccountStatus() != AccountStatus.LOCKED) {
            throw new AccountManagementException("Chỉ có thể mở khóa tài khoản đang bị khóa.");
        }
        user.setAccountStatus(Boolean.TRUE.equals(user.getEmailVerified())
                ? AccountStatus.ACTIVE
                : AccountStatus.PENDING_VERIFICATION);
        users.save(user);
    }

    private User findUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new AccountManagementException("Không tìm thấy tài khoản."));
    }

    private void requireNonAdmin(User user) {
        if (user.getRole() == Role.ADMIN) {
            throw new AccountManagementException("Không thể khóa hoặc mở khóa tài khoản Admin.");
        }
    }

    public static class AccountManagementException extends RuntimeException {
        public AccountManagementException(String message) {
            super(message);
        }
    }
}
