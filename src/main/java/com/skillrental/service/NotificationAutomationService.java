package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.notification.OrderStatusChangedEvent;
import com.skillrental.repository.ComplaintRepository;
import com.skillrental.repository.ConversationRepository;
import com.skillrental.repository.RentalOrderRepository;
import com.skillrental.repository.UserRepository;
import com.skillrental.websocket.ChatMessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
@RequiredArgsConstructor
public class NotificationAutomationService {
    private final NotificationService notifications;
    private final RentalOrderRepository orders;
    private final UserRepository users;
    private final ComplaintRepository complaints;
    private final ConversationRepository conversations;

    @TransactionalEventListener
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onChatMessage(ChatMessageSentEvent event) {
        Conversation conversation=conversations.findById(event.conversationId()).orElse(null);
        if(conversation==null)return;
        if(Boolean.TRUE.equals(conversation.getSupportConversation()) && event.recipientId()==null) {
            users.findAll().stream().filter(user->user.getRole()==Role.ADMIN
                            && user.getAccountStatus()==AccountStatus.ACTIVE)
                    .forEach(admin->createChatNotification(admin,event));
            return;
        }
        if(event.recipientId()!=null)users.findById(event.recipientId())
                .filter(recipient->recipient.getRole()!=Role.ADMIN || Boolean.TRUE.equals(conversation.getSupportConversation()))
                .ifPresent(recipient->createChatNotification(recipient,event));
    }

    private void createChatNotification(User recipient,ChatMessageSentEvent event) {
        notifications.create(recipient,"CHAT_MESSAGE","Tin nhắn mới từ "+event.senderName(),
                event.content(),"CONVERSATION",event.conversationId(),
                "/messages?conversationId="+event.conversationId());
    }

    @TransactionalEventListener
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        RentalOrder order = orders.findById(event.orderId()).orElse(null);
        if (order == null || order.getCustomer() == null) return;
        if (order.getProvider() == null) {
            notifyAdmins("ORDER_REVIEW", "Đơn hàng cần xử lý",
                    "Đơn hàng #" + order.getId() + " chưa được gán Provider.",
                    "ORDER", order.getId(), "/admin/orders");
            return;
        }
        User recipient = event.actorId() != null && event.actorId().equals(order.getCustomer().getId())
                ? order.getProvider() : order.getCustomer();
        boolean deliverySent = "PENDING_CUSTOMER_ACCEPTANCE".equals(event.newStatus())
                && event.actorId() != null && order.getProvider().getId().equals(event.actorId());
        if (deliverySent) return;
        boolean revisionRequested = "PROCESSING".equals(event.newStatus())
                && "PENDING_CUSTOMER_ACCEPTANCE".equals(event.previousStatus())
                && event.actorId() != null && order.getCustomer().getId().equals(event.actorId());
        if (revisionRequested) return;
        String message = switch (event.newStatus()) {
            case "PENDING_PROVIDER_ACCEPTANCE" -> "Customer đã tạo yêu cầu đặt dịch vụ mới.";
            case "PENDING_START_CONFIRMATION" -> "Provider đã nhận đơn hàng.";
            case "PROCESSING" -> "Hai bên đã xác nhận bắt đầu công việc.";
            case "PENDING_CUSTOMER_ACCEPTANCE" -> "Provider đã gửi sản phẩm bàn giao và đang chờ bạn nghiệm thu.";
            case "PENDING_PAYMENT_OTP" -> "Customer đã nghiệm thu; đơn đang chờ OTP quyết toán.";
            case "COMPLETED" -> "Đơn hàng đã hoàn tất và được quyết toán.";
            case "CANCELLED" -> "Đơn hàng đã bị hủy.";
            case "CANCELLATION_REQUESTED_BY_CUSTOMER" -> "Customer đã yêu cầu hủy đơn hàng.";
            case "CANCELLATION_COMPENSATION_PENDING_CUSTOMER" -> "Provider đã đề xuất chi phí cho yêu cầu hủy.";
            default -> "Đơn hàng đã cập nhật trạng thái: " + event.newStatus() + ".";
        };
        String targetUrl="/orders/" + order.getId() + "/progress";
        boolean newOrder=event.previousStatus()==null&&"PENDING_PROVIDER_ACCEPTANCE".equals(event.newStatus());
        notifications.create(recipient, newOrder?"ORDER_CREATED":"ORDER_STATUS", (newOrder?"Đơn hàng mới #":"Cập nhật đơn hàng #") + order.getId(),
                message, "ORDER", order.getId(), targetUrl);
        if ("COMPLETED".equals(event.newStatus())) {
            User actor=users.findById(event.actorId()).orElse(null);
            if(actor!=null)notifications.create(actor,"ORDER_SETTLED","Đơn hàng đã quyết toán",
                    "Đơn #" + order.getId() + " đã được hoàn tất và quyết toán.",
                    "ORDER",order.getId(),actor.getRole()==Role.CUSTOMER
                            ?"/customer/wallet":"/provider/wallet");
        }
    }

    public void notifyAdmins(String type, String title, String content, String targetType,
                             Long targetId, String targetUrl) {
        users.findAll().stream().filter(user -> user.getRole() == Role.ADMIN
                        && user.getAccountStatus() == AccountStatus.ACTIVE)
                .forEach(admin -> notifications.create(admin, type, title, content,
                        targetType, targetId, targetUrl));
    }

    public void notifyUser(Long userId, String type, String title, String content,
                           String targetType, Long targetId, String targetUrl) {
        users.findById(userId).ifPresent(user -> notifications.create(user, type, title,
                content, targetType, targetId, targetUrl));
    }
}
