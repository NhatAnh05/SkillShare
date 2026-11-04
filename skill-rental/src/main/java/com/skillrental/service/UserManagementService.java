package com.skillrental.service;

import com.skillrental.entity.AccountStatus;
import com.skillrental.entity.Role;
import com.skillrental.entity.User;
import com.skillrental.repository.*;
import com.skillrental.entity.UserRoleChange;
import com.skillrental.entity.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserManagementService {
    private final UserRepository users;
    private final NotificationService notifications;
    private final UserRoleChangeRepository roleChanges;
    private final RentalOrderRepository orders;
    private final UserWalletRepository wallets;
    private final ProviderWithdrawalRepository withdrawals;

    @Transactional
    public void lock(Long userId) {
        User user = findUser(userId);
        requireNonAdmin(user);
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountManagementException("Chỉ có thể khóa tài khoản đang hoạt động.");
        }
        user.setAccountStatus(AccountStatus.LOCKED);
        users.save(user);
        notifications.create(user, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa",
                "Admin đã khóa tài khoản của bạn.", "USER", user.getId(),
                user.getRole() == Role.PROVIDER ? "/provider/dashboard" : "/marketplace");
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
        notifications.create(user, "ACCOUNT_UNLOCKED", "Tài khoản đã được mở khóa",
                "Admin đã mở khóa tài khoản của bạn.", "USER", user.getId(),
                user.getRole() == Role.PROVIDER ? "/provider/dashboard" : "/marketplace");
    }

    @Transactional
    public boolean promoteToAdmin(Long targetId, User requestedActor, String requestedRole, String reason) {
        User actor = requestedActor == null ? null : users.findById(requestedActor.getId()).orElse(null);
        if (actor == null || actor.getRole() != Role.ADMIN || actor.getAccountStatus() != AccountStatus.ACTIVE
                || !Boolean.TRUE.equals(actor.getEmailVerified())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Chỉ Admin đang hoạt động mới được cấp quyền Admin.");
        }
        if (!"ADMIN".equals(requestedRole)) throw new AccountManagementException("Thao tác này chỉ hỗ trợ nâng Customer hoặc Provider lên Admin.");
        String note = reason == null ? "" : reason.strip();
        if (note.isEmpty() || note.length() > 500) throw new AccountManagementException("Nhập lý do cấp quyền Admin (tối đa 500 ký tự).");
        User target = users.findByIdForRoleUpdate(targetId).orElseThrow(() -> new AccountManagementException("Không tìm thấy tài khoản."));
        if (target.getRole() == Role.ADMIN) return false;
        if (target.getAccountStatus() != AccountStatus.ACTIVE || !Boolean.TRUE.equals(target.getEmailVerified()))
            throw new AccountManagementException("Chỉ cấp quyền cho tài khoản đang hoạt động và đã xác minh email.");
        if (orders.hasUnfinishedOrders(targetId, java.util.List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED)))
            throw new AccountManagementException("Tài khoản còn đơn chưa kết thúc. Hãy hoàn tất hoặc xử lý hủy đơn trước khi đổi sang Admin.");
        Role previous = target.getRole();
        if (previous == Role.PROVIDER) {
            boolean moneyRemaining = wallets.findByUser(target).map(w -> w.getBalance().signum() > 0 || w.getLockedAmount().signum() > 0).orElse(false);
            boolean withdrawalPending = withdrawals.findByProviderOrderByCreatedAtDesc(target).stream()
                    .anyMatch(w -> "PENDING_OTP".equals(w.getStatus()) || "PENDING_ADMIN".equals(w.getStatus()));
            if (moneyRemaining || withdrawalPending) throw new AccountManagementException("Provider cần xử lý số dư ví và yêu cầu rút tiền còn chờ trước khi đổi sang Admin.");
        }
        target.setRole(Role.ADMIN);
        users.save(target);
        roleChanges.save(UserRoleChange.builder().target(target).actor(actor).previousRole(previous).newRole(Role.ADMIN).reason(note).build());
        notifications.create(target, actor, "ACCOUNT_ROLE_CHANGED", "Tài khoản đã được cấp quyền Admin",
                "Admin " + actor.getFullName() + " đã cấp quyền Admin cho bạn. Hãy đăng nhập lại để cập nhật quyền.",
                "USER", targetId, "/admin/dashboard");
        return true;
    }

    @Transactional(readOnly = true)
    public java.util.List<UserRoleChange> recentRoleChanges() {
        return roleChanges.findTop20ByOrderByChangedAtDescIdDesc();
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
