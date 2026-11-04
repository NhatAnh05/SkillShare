package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import com.skillrental.repository.UserRepository;
import com.skillrental.websocket.ChatMessageSentEvent;
import com.skillrental.websocket.ChatReadEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor public class ChatService {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UserRepository users;
    private final SupportAssignmentHistoryRepository assignmentHistory;
    private final ApplicationEventPublisher events;
    private final com.skillrental.repository.SupportNoteRepository supportNotes;
    @Transactional(readOnly=true)
    public List<Conversation> list(User u) {
        return conversations.findAccessibleTo(u,u.getRole()==Role.ADMIN);
    }
    @Transactional(readOnly=true)
    public Conversation requireParticipant(Long id,User u) {
        var c=conversations.findById(id).orElseThrow();
        if(!isParticipant(c,u))throw new IllegalArgumentException("Không có quyền truy cập hội thoại");
        return c;
    }
    public Conversation owned(Long id,User u) { return requireParticipant(id,u); }
    public boolean isParticipant(Conversation c,User u) {
        if (u == null || c == null) return false;
        if (Boolean.TRUE.equals(c.getSupportConversation())) {
            if (u.getRole() == Role.ADMIN) {
                return c.getSupportAdmin() == null || c.getSupportAdmin().getId().equals(u.getId());
            }
            return (c.getCustomer() != null && c.getCustomer().getId().equals(u.getId()))
                    || (c.getProvider() != null && c.getProvider().getId().equals(u.getId()));
        }
        return (c.getCustomer()!=null && c.getCustomer().getId().equals(u.getId()))
                || (c.getProvider()!=null && c.getProvider().getId().equals(u.getId()));
    }
    @Transactional(readOnly=true)
    public List<Message> messages(Conversation c) {
        return messages.findByConversationOrderBySentAtAsc(c);
    }
    @Transactional
    public Message send(Conversation c,User u,String text) {
        return send(c,u,text,null);
    }
    @Transactional
    public Message send(Conversation c,User u,String text,String clientKey) {
        c=conversations.findByIdForUpdate(c.getId()).orElseThrow();
        if(!isParticipant(c,u))throw new IllegalArgumentException("Không có quyền gửi tin nhắn");
        if(clientKey!=null && !clientKey.isBlank()) {
            try { java.util.UUID.fromString(clientKey); } catch(RuntimeException ex) { throw new IllegalArgumentException("Mã tin nhắn không hợp lệ."); }
            var previous=messages.findBySenderAndClientKey(u,clientKey);
            if(previous.isPresent()) {
                if(!previous.get().getConversation().getId().equals(c.getId()) || !previous.get().getContent().equals(text.trim()))
                    throw new IllegalArgumentException("Mã tin nhắn đã dùng cho nội dung khác.");
                return previous.get();
            }
        } else clientKey=null;
        if(u.getRole()==Role.ADMIN && messages.countBySenderAndSentAtAfter(u,LocalDateTime.now().minusMinutes(1))>=40)
            throw new IllegalArgumentException("Đã đạt giới hạn 40 tin nhắn/phút; hãy thử lại sau.");
        if(text==null || text.isBlank() || text.length()>2500)throw new IllegalArgumentException("Nội dung tin nhắn không hợp lệ");
        Message saved=messages.save(Message.builder().conversation(c).sender(u).clientKey(clientKey).content(text.trim()).sentAt(LocalDateTime.now()).build());
        c.setLastMessageAt(saved.getSentAt());
        conversations.save(c);
        if (Boolean.TRUE.equals(c.getSupportConversation()) && u.getRole() == Role.ADMIN && c.getSupportAdmin() == null) {
            assignSupport(c, u, "CLAIMED");
        }
        Long recipientId;
        if (Boolean.TRUE.equals(c.getSupportConversation())) {
            recipientId=u.getRole()==Role.ADMIN
                    ? (c.getCustomer()!=null?c.getCustomer().getId():c.getProvider().getId())
                    : c.getSupportAdmin()==null?null:c.getSupportAdmin().getId();
        } else {
            recipientId=c.getCustomer().getId().equals(u.getId())
                    ? c.getProvider().getId():c.getCustomer().getId();
        }
        events.publishEvent(new ChatMessageSentEvent(c.getId(),saved.getId(),u.getId(),recipientId,u.getFullName(),
                saved.getContent(),saved.getSentAt()));
        return saved;
    }
    @Transactional
    public void claimSupport(Long conversationId, User admin) {
        if (admin == null || admin.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Chỉ Admin mới được phân công hội thoại hỗ trợ.");
        }
        Conversation conversation = conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hội thoại."));
        if (!Boolean.TRUE.equals(conversation.getSupportConversation())) {
            throw new IllegalArgumentException("Đây không phải hội thoại hỗ trợ.");
        }
        if (conversation.getSupportAdmin() != null
                && !conversation.getSupportAdmin().getId().equals(admin.getId())) {
            throw new IllegalStateException("Hội thoại đã được Admin khác tiếp nhận.");
        }
        if (conversation.getSupportAdmin() == null) assignSupport(conversation, admin, "CLAIMED");
    }

    @Transactional
    public void releaseSupport(Long conversationId, User admin) {
        Conversation conversation = conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hội thoại."));
        if (admin == null || admin.getRole() != Role.ADMIN
                || !Boolean.TRUE.equals(conversation.getSupportConversation())
                || conversation.getSupportAdmin() == null
                || !conversation.getSupportAdmin().getId().equals(admin.getId())) {
            throw new IllegalArgumentException("Chỉ Admin được phân công mới có thể trả hội thoại về hàng đợi.");
        }
        User previous = conversation.getSupportAdmin();
        conversation.setSupportAdmin(null);
        conversations.save(conversation);
        membershipChanged(conversation,previous);
        assignmentHistory.save(SupportAssignmentHistory.builder().conversation(conversation)
                .changedBy(admin).previousAdmin(previous).assignedAdmin(null).action("RELEASED").build());
    }

    private void assignSupport(Conversation conversation, User admin, String action) {
        User previous = conversation.getSupportAdmin();
        conversation.setSupportAdmin(admin);
        conversations.save(conversation);
        membershipChanged(conversation,previous);
        assignmentHistory.save(SupportAssignmentHistory.builder().conversation(conversation)
                .changedBy(admin).previousAdmin(previous).assignedAdmin(admin).action(action).build());
    }
    private void membershipChanged(Conversation c,User previous){
        var ids=new java.util.HashSet<Long>();
        if(c.getCustomer()!=null)ids.add(c.getCustomer().getId());if(c.getProvider()!=null)ids.add(c.getProvider().getId());if(previous!=null)ids.add(previous.getId());
        if(c.getSupportAdmin()!=null)ids.add(c.getSupportAdmin().getId());users.findByRole(Role.ADMIN).forEach(u->ids.add(u.getId()));
        events.publishEvent(new com.skillrental.websocket.ChatMembershipChangedEvent(c.getId(),new java.util.ArrayList<>(ids)));
    }
    @Transactional public void transferSupport(Long id,User actor,Long adminId,String reason){
        var c=conversations.findByIdForUpdate(id).orElseThrow();
        if(actor.getRole()!=Role.ADMIN||!Boolean.TRUE.equals(c.getSupportConversation())||c.getSupportAdmin()==null||!c.getSupportAdmin().getId().equals(actor.getId()))throw new IllegalArgumentException("Chỉ người phụ trách mới được chuyển ca.");
        if(reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("Cần lý do chuyển ca (tối đa 500 ký tự).");
        var next=users.findById(adminId).filter(u->u.getRole()==Role.ADMIN&&u.getAccountStatus()==AccountStatus.ACTIVE).orElseThrow(()->new IllegalArgumentException("Admin tiếp nhận không hợp lệ."));
        if(next.getId().equals(actor.getId()))throw new IllegalArgumentException("Hãy chọn Admin khác.");
        assignSupport(c,next,"TRANSFERRED");
        // Immutable note contains the transfer reason; assignment history identifies both administrators.
        supportNotes.save(com.skillrental.entity.SupportNote.builder().conversation(c).admin(actor).content("Chuyển ca cho "+next.getFullName()+": "+reason.strip()).build());
    }
    @Transactional(readOnly=true)
    public List<Message> page(Conversation c,Long beforeId) {
        var result=new ArrayList<>(messages.findTop50ByConversationAndIdLessThanOrderByIdDesc(c,beforeId==null?Long.MAX_VALUE:beforeId));
        Collections.reverse(result);return result;
    }
    @Transactional
    public int markRead(Conversation c,User reader) {
        if(!isParticipant(c,reader))throw new IllegalArgumentException("Không có quyền truy cập hội thoại");
        int updated=messages.markUnreadMessagesRead(c,reader.getId(),LocalDateTime.now());
        if(updated>0)events.publishEvent(new ChatReadEvent(c.getId(),reader.getId()));
        return updated;
    }
    @Transactional(readOnly=true)
    public long unreadCount(Conversation c,User reader) {
        return messages.countUnreadByConversationAndReader(c,reader.getId());
    }
}
