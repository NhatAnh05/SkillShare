package com.skillrental.workspace;

import com.skillrental.entity.User;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class WorkspaceState {

    private final Set<Long> blockedUsers = ConcurrentHashMap.newKeySet();
    private final Set<Long> approvedProviders = ConcurrentHashMap.newKeySet();
    private final Set<Long> approvedSkills = ConcurrentHashMap.newKeySet();
    private final Set<Long> approvedServices = ConcurrentHashMap.newKeySet();
    private final Set<Long> blockedServices = ConcurrentHashMap.newKeySet();
    private final Set<Long> hiddenReviews = ConcurrentHashMap.newKeySet();
    private final Set<Long> resolvedComplaints = ConcurrentHashMap.newKeySet();
    private final Set<String> approvedWithdrawals = ConcurrentHashMap.newKeySet();
    private final Set<String> enabledProviderPackages = ConcurrentHashMap.newKeySet();
    private final Map<Long, String> providerModes = new ConcurrentHashMap<>();
    private final List<Post> posts = new CopyOnWriteArrayList<>();

    private volatile boolean demoPrimed;

    public WorkspaceState() {
        enabledProviderPackages.add("BASIC");
        enabledProviderPackages.add("PRO");

        posts.add(new Post(
                "POST-01",
                "Quy trình thiết kế Landing Page giúp tối ưu chuyển đổi",
                "Thiết kế UI/UX",
                "Chia sẻ quy trình phân tích yêu cầu, thiết kế wireframe, prototype và tối ưu trải nghiệm người dùng.",
                "Đã xuất bản",
                1420,
                98
        ));

        posts.add(new Post(
                "POST-02",
                "Top 10 giải pháp tối ưu hiệu năng ReactJS",
                "Lập trình Web",
                "Tổng hợp các kỹ thuật tối ưu render, chia bundle, cache và kiểm soát state trong ứng dụng web.",
                "Bản nháp",
                850,
                45
        ));
    }

    public synchronized void primeDemo(Collection<User> users) {
        if (demoPrimed) {
            return;
        }

        // Keep the agreed demo database to exactly three accounts.
        // Admin can still block/unblock accounts through the workspace actions.
        demoPrimed = true;
    }

    public void reset() {
        blockedUsers.clear();
        approvedProviders.clear();
        approvedSkills.clear();
        approvedServices.clear();
        blockedServices.clear();
        hiddenReviews.clear();
        resolvedComplaints.clear();
        approvedWithdrawals.clear();
        providerModes.clear();
        enabledProviderPackages.clear();
        enabledProviderPackages.add("BASIC");
        enabledProviderPackages.add("PRO");
        demoPrimed = false;
    }

    public boolean isBlocked(Long id) {
        return blockedUsers.contains(id);
    }

    public void blockUser(Long id) {
        blockedUsers.add(id);
    }

    public void unblockUser(Long id) {
        blockedUsers.remove(id);
    }

    public Set<Long> blockedUserIds() {
        return Set.copyOf(blockedUsers);
    }

    public boolean isProviderApproved(Long id) {
        return approvedProviders.contains(id);
    }

    public void approveProvider(Long id) {
        approvedProviders.add(id);
    }

    public boolean isSkillApproved(Long id) {
        return approvedSkills.contains(id);
    }

    public void approveSkill(Long id) {
        approvedSkills.add(id);
    }

    public boolean isServiceApproved(Long id) {
        return approvedServices.contains(id);
    }

    public void approveService(Long id) {
        approvedServices.add(id);
    }

    public void blockService(Long id) {
        blockedServices.add(id);
        approvedServices.remove(id);
    }

    public void restoreService(Long id) {
        blockedServices.remove(id);
    }

    public boolean isServiceBlocked(Long id) {
        return blockedServices.contains(id);
    }

    public boolean isReviewHidden(Long id) {
        return hiddenReviews.contains(id);
    }

    public void hideReview(Long id) {
        hiddenReviews.add(id);
    }

    public void restoreReview(Long id) {
        hiddenReviews.remove(id);
    }

    public boolean isComplaintResolved(Long id) {
        return resolvedComplaints.contains(id);
    }

    public void resolveComplaint(Long id) {
        resolvedComplaints.add(id);
    }

    public void approveWithdrawal(String id) {
        approvedWithdrawals.add(id);
    }

    public boolean isWithdrawalApproved(String id) {
        return approvedWithdrawals.contains(id);
    }

    public boolean isPackageEnabled(String code) {
        return enabledProviderPackages.contains(code);
    }

    public Set<String> enabledPackageCodes() {
        return Set.copyOf(enabledProviderPackages);
    }

    public void togglePackage(String code) {
        if (enabledProviderPackages.contains(code)) {
            enabledProviderPackages.remove(code);
        } else {
            enabledProviderPackages.add(code);
        }
    }

    public String providerMode(Long providerId) {
        return providerModes.getOrDefault(providerId, "AVAILABLE");
    }

    public void setProviderMode(Long providerId, String mode) {
        providerModes.put(providerId, mode);
    }

    public List<Post> posts() {
        return List.copyOf(posts);
    }

    public void addPost(String title, String category, String content) {
        posts.add(0, new Post(
                "POST-" + System.currentTimeMillis(),
                title,
                category,
                content,
                "Bản nháp",
                0,
                0
        ));
    }

    public void deletePost(String id) {
        posts.removeIf(post -> post.id().equals(id));
    }

    public record Post(
            String id,
            String title,
            String category,
            String content,
            String status,
            int views,
            int likes
    ) {
    }
}
