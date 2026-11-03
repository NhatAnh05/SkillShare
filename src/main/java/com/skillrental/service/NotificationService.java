package com.skillrental.service;

import com.skillrental.entity.Notification;
import com.skillrental.entity.User;
import com.skillrental.notification.NotificationCreatedEvent;
import com.skillrental.notification.NotificationReadEvent;
import com.skillrental.repository.NotificationRepository;
import com.skillrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    @Transactional
    public Notification create(User recipient, String type, String title, String content,
                               String targetType, Long targetId, String targetUrl) {
        return create(recipient, null, type, title, content, targetType, targetId, targetUrl);
    }

    @Transactional
    public Notification create(User recipient, User sender, String type, String title, String content,
                               String targetType, Long targetId, String targetUrl) {
        if (recipient == null || recipient.getId() == null) {
            throw new IllegalArgumentException("Thông báo cần người nhận hợp lệ.");
        }
        boolean openNotificationDetail = targetUrl == null || targetUrl.isBlank();
        String safeUrl = openNotificationDetail || !targetUrl.startsWith("/") || targetUrl.startsWith("//")
                ? "/marketplace" : targetUrl;
        Notification saved = notifications.save(Notification.builder()
                .recipient(recipient).sender(sender).type(type).title(preview(title,180)).content(preview(content,1200))
                .targetType(targetType).targetId(targetId).targetUrl(safeUrl).build());
        if (openNotificationDetail) {
            saved.setTargetUrl("/notifications/item/" + saved.getId());
            saved = notifications.save(saved);
        }
        events.publishEvent(new NotificationCreatedEvent(saved.getId(), recipient.getId()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Notification> latest(User recipient) {
        return notifications.findTop50ByRecipientOrderByCreatedAtDescIdDesc(recipient);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Notification> inbox(User recipient, int page) {
        return notifications.findByRecipientOrderByCreatedAtDescIdDesc(recipient,
                org.springframework.data.domain.PageRequest.of(Math.max(0, page), 50));
    }

    @Transactional(readOnly = true)
    public Optional<Notification> findForRecipient(Long id, User recipient) {
        return notifications.findByIdAndRecipient(id, recipient);
    }

    @Transactional(readOnly = true)
    public long unreadCount(User recipient) {
        return notifications.countByRecipientAndReadAtIsNull(recipient);
    }

    @Transactional
    public boolean markRead(Long id, User recipient) {
        return notifications.findByIdAndRecipient(id, recipient).map(notification -> {
            if (notification.getReadAt() == null) {
                notification.setReadAt(LocalDateTime.now());
                notifications.save(notification);
                events.publishEvent(new NotificationReadEvent(recipient.getId(), id,
                        notifications.countByRecipientAndReadAtIsNull(recipient)));
            }
            return true;
        }).orElse(false);
    }

    @Transactional
    public int markAllRead(User recipient) {
        int changed = notifications.markAllRead(recipient, LocalDateTime.now());
        events.publishEvent(new NotificationReadEvent(recipient.getId(), null,
                notifications.countByRecipientAndReadAtIsNull(recipient)));
        return changed;
    }

    private String preview(String text,int limit){if(text==null)return "";return text.length()<=limit?text:text.substring(0,limit-1)+"…";}
    @Transactional
    public void notifyAdmins(String type, String title, String content, String targetType, Long targetId, String url) {
        users.findByRole(com.skillrental.entity.Role.ADMIN).stream()
                .filter(admin -> admin.getAccountStatus() == com.skillrental.entity.AccountStatus.ACTIVE)
                .forEach(admin ->
                create(admin, type, title, content, targetType, targetId, url));
    }
}
