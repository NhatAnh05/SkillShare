package com.skillrental.service;

import com.skillrental.dto.AdminNotificationRequest;
import com.skillrental.entity.*;
import com.skillrental.notification.NotificationCampaignQueuedEvent;
import com.skillrental.repository.NotificationCampaignRecipientRepository;
import com.skillrental.repository.NotificationCampaignRepository;
import com.skillrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationCampaignService {
    private static final Set<String> TYPES = Set.of(
            "ADMIN_GENERAL", "ADMIN_IMPORTANT", "ADMIN_MAINTENANCE", "ADMIN_POLICY");
    private static final Set<String> AUDIENCES = Set.of(
            "ALL_CUSTOMERS", "ALL_PROVIDERS", "ALL_MARKETPLACE_USERS", "SELECTED");

    private final NotificationCampaignRepository campaigns;
    private final NotificationCampaignRecipientRepository recipients;
    private final UserRepository users;
    private final CurrentUserService current;
    private final ApplicationEventPublisher events;
    private final NotificationCampaignDeliveryService delivery;
    private final com.skillrental.repository.NotificationCampaignAuditRepository audit;

    @Transactional(readOnly = true)
    public List<User> searchRecipients(String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || (normalized.length() < 2 && !normalized.matches("[0-9]+"))) return List.of();
        Long id = null;
        try { id = Long.valueOf(normalized); } catch (NumberFormatException ignored) { }
        String term = "%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return users.searchNotificationRecipients(term, id, PageRequest.of(0, 25));
    }

    @Transactional(readOnly = true)
    public Preview preview(String audience, List<Long> recipientIds) {
        validateAudience(audience);
        return new Preview(audience, resolveRecipients(audience, recipientIds).size());
    }

    @Transactional
    public NotificationCampaign send(User admin, AdminNotificationRequest request) {
        requireAdmin(admin);
        // Serialize submissions by this administrator, including concurrent double clicks.
        User lockedAdmin = users.findByIdForNotificationUpdate(admin.getId()).orElseThrow();
        requireAdmin(lockedAdmin);
        validate(request);
        Optional<NotificationCampaign> existing = campaigns.findByRequestKey(request.requestKey());
        if (existing.isPresent()) {
            NotificationCampaign previous = existing.get();
            if (!previous.getSender().getId().equals(admin.getId())
                    || !previous.getTitle().equals(request.title().strip())
                    || !previous.getContent().equals(request.content().strip())
                    || !previous.getType().equals(request.type())
                    || !previous.getAudience().equals(request.audience())
                    || !Objects.equals(previous.getTargetUrl(), validateTargetUrl(request.targetUrl()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Mã gửi đã được sử dụng cho nội dung khác.");
            }
            if ("SELECTED".equals(request.audience())) {
                List<Long> previousIds = recipients.findByCampaignIdOrderByIdAsc(previous.getId()).stream()
                        .map(row -> row.getRecipient().getId()).sorted().toList();
                List<Long> requestedIds = request.recipientIds() == null ? List.of()
                        : request.recipientIds().stream().sorted().toList();
                if (!previousIds.equals(requestedIds)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Mã gửi đã được sử dụng cho danh sách người nhận khác.");
                }
            }
            return previous;
        }

        List<User> targets = resolveRecipients(request.audience(), request.recipientIds());
        if (targets.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không có người nhận đủ điều kiện.");
        NotificationCampaign campaign = campaigns.save(NotificationCampaign.builder()
                .sender(admin)
                .requestKey(request.requestKey())
                .title(cleanText(request.title(), 180, "Tiêu đề"))
                .content(cleanText(request.content(), 1200, "Nội dung"))
                .type(request.type())
                .audience(request.audience())
                .targetUrl(validateTargetUrl(request.targetUrl()))
                .build());
        recipients.saveAll(targets.stream().map(user -> NotificationCampaignRecipient.builder()
                .campaign(campaign).recipient(user).deliveryStatus("QUEUED").build()).toList());
        audit.save(NotificationCampaignAudit.builder().campaign(campaign).actor(lockedAdmin)
                .action("SEND").recipientCount(targets.size()).build());
        events.publishEvent(new NotificationCampaignQueuedEvent(campaign.getId()));
        return campaign;
    }

    @Transactional(readOnly = true)
    public List<CampaignSummary> history() {
        return campaigns.findTop50ByOrderByCreatedAtDescIdDesc().stream()
                .map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public CampaignDetail detail(Long campaignId, int page) {
        NotificationCampaign campaign = campaigns.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int pageNumber = Math.max(0, page);
        int pageSize = 100;
        var recipientPage = recipients.findByCampaignIdOrderByIdAsc(campaignId,
                PageRequest.of(pageNumber, pageSize));
        List<RecipientDetail> rows = recipientPage.getContent().stream()
                .map(row -> new RecipientDetail(row.getRecipient().getId(), row.getRecipient().getFullName(),
                        row.getRecipient().getEmail(), row.getDeliveryStatus(),
                        row.getNotification() != null && row.getNotification().getReadAt() != null,
                        row.getLastError()))
                .toList();
        CampaignSummary summary = summary(campaign);
        return new CampaignDetail(summary, campaign.getContent(), campaign.getTargetUrl(),
                recipientPage.getTotalElements(), pageNumber, pageSize, rows);
    }

    @Transactional
    public void retryFailures(User admin, Long campaignId) {
        requireAdmin(admin);
        NotificationCampaign retryCampaign = campaigns.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int retryCount = 0;
        boolean queued = false;
        for (Long id : recipients.findRetryableIds(campaignId)) {
            var row = recipients.findByIdForUpdate(id).orElseThrow();
            if (("FAILED".equals(row.getDeliveryStatus()) || "QUEUED".equals(row.getDeliveryStatus()))
                    && row.getNotification() == null) {
                row.setDeliveryStatus("QUEUED");
                row.setLastError(null);
                retryCount++;
                queued = true;
            } else if ("WS_FAILED".equals(row.getDeliveryStatus())) {
                delivery.retryWebSocket(id);
                retryCount++;
            }
        }
        if (retryCount == 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Không có người nhận thất bại để thử lại.");
        audit.save(NotificationCampaignAudit.builder().campaign(retryCampaign).actor(admin)
                .action("RETRY").recipientCount(retryCount).build());
        if (queued) events.publishEvent(new NotificationCampaignQueuedEvent(campaignId));
    }

    @Transactional(readOnly = true)
    public List<AuditDetail> auditHistory(Long campaignId) {
        return audit.findByCampaignIdOrderByIdAsc(campaignId).stream()
                .map(row -> new AuditDetail(row.getActor().getFullName(), row.getAction(),
                        row.getRecipientCount(), row.getCreatedAt())).toList();
    }

    public record AuditDetail(String actor, String action, int recipientCount,
                              java.time.LocalDateTime createdAt) {}

    public void recordWebSocketAttempt(Long notificationId, int successfulSessions, int failedSessions) {
        delivery.recordWebSocketAttempt(notificationId, successfulSessions, failedSessions);
    }

    private CampaignSummary summary(NotificationCampaign campaign) {
        long total = recipients.countByCampaignId(campaign.getId());
        long read = recipients.countReadByCampaignId(campaign.getId());
        long unread = recipients.countUnreadByCampaignId(campaign.getId());
        long queued = recipients.countQueuedByCampaignId(campaign.getId());
        long failed = recipients.countFailuresByCampaignId(campaign.getId());
        String status = queued > 0 ? "Đang phân phối" : failed > 0 ? "Hoàn tất, có lỗi" : "Đã lưu";
        return new CampaignSummary(campaign.getId(), campaign.getTitle(), campaign.getType(),
                campaign.getAudience(), total, read, unread, failed, campaign.getSender().getFullName(),
                campaign.getCreatedAt(), campaign.getSentAt(), status,
                recipients.countByCampaignIdAndNotificationIsNotNull(campaign.getId()), queued,
                recipients.countByCampaignIdAndDeliveryStatus(campaign.getId(), "ONLINE"),
                recipients.countByCampaignIdAndDeliveryStatus(campaign.getId(), "OFFLINE"));
    }

    private List<User> resolveRecipients(String audience, List<Long> recipientIds) {
        validateAudience(audience);
        Set<Role> allowedRoles = switch (audience) {
            case "ALL_CUSTOMERS" -> Set.of(Role.CUSTOMER);
            case "ALL_PROVIDERS" -> Set.of(Role.PROVIDER);
            case "ALL_MARKETPLACE_USERS", "SELECTED" -> Set.of(Role.CUSTOMER, Role.PROVIDER);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đối tượng nhận không hợp lệ.");
        };
        if ("SELECTED".equals(audience)) {
            if (recipientIds == null || recipientIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hãy chọn ít nhất một người nhận.");
            }
            LinkedHashSet<Long> ids = new LinkedHashSet<>(recipientIds);
            if (ids.size() != recipientIds.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách người nhận có ID trùng.");
            }
            Map<Long, User> eligible = new HashMap<>();
            eligibleUsers().stream().filter(user -> allowedRoles.contains(user.getRole()))
                    .forEach(user -> eligible.put(user.getId(), user));
            if (ids.stream().anyMatch(id -> !eligible.containsKey(id))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Chỉ được chọn Customer hoặc Provider đang hoạt động và đã xác minh email.");
            }
            return ids.stream().map(eligible::get).toList();
        }
        return eligibleUsers().stream().filter(user -> allowedRoles.contains(user.getRole())).toList();
    }

    private List<User> eligibleUsers() {
        return users.findNotificationRecipients(Set.of(Role.CUSTOMER, Role.PROVIDER));
    }

    private void validate(AdminNotificationRequest request) {
        if (!TYPES.contains(request.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại thông báo không hợp lệ.");
        }
        validateAudience(request.audience());
        cleanText(request.title(), 180, "Tiêu đề");
        cleanText(request.content(), 1200, "Nội dung");
        validateTargetUrl(request.targetUrl());
        try {
            UUID.fromString(request.requestKey());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã yêu cầu không hợp lệ.");
        }
    }

    private void validateAudience(String audience) {
        if (!AUDIENCES.contains(audience)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đối tượng nhận không hợp lệ.");
        }
    }

    private String cleanText(String value, int maxLength, String label) {
        String text = value == null ? "" : value.strip();
        if (text.isBlank() || text.length() > maxLength || text.contains("<") || text.contains(">")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " không hợp lệ; chỉ hỗ trợ văn bản thuần.");
        }
        return text;
    }

    private String validateTargetUrl(String targetUrl) {
        return NotificationLinks.validate(targetUrl);
    }

    public void requireAdmin(User admin) {
        if (admin == null || admin.getRole() != Role.ADMIN
                || admin.getAccountStatus() != AccountStatus.ACTIVE
                || !Boolean.TRUE.equals(admin.getEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Admin đang hoạt động mới được gửi thông báo.");
        }
    }

    public record Preview(String audience, int recipientCount) {}
    public record CampaignSummary(Long id, String title, String type, String audience, long recipientCount,
                                  long readCount, long unreadCount, long failedCount, String sender,
                                  java.time.LocalDateTime createdAt, java.time.LocalDateTime sentAt, String status,
                                  long storedCount, long queuedCount, long websocketSentCount, long offlineCount) {}
    public record RecipientDetail(Long userId, String name, String email, String deliveryStatus,
                                  boolean read, String error) {}
    public record CampaignDetail(CampaignSummary campaign, String content, String targetUrl, long totalRows,
                                 int page, int pageSize, List<RecipientDetail> recipients) {}
}
