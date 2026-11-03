package com.skillrental.service;
import com.skillrental.entity.*;import com.skillrental.repository.*;import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;import org.springframework.web.server.ResponseStatusException;
import java.time.*;import java.util.*;
@Service @RequiredArgsConstructor
public class SupportManagementService {
 private final ConversationRepository conversations;private final UserRepository users;
 private final SupportAssignmentHistoryRepository audit;private final SupportNoteRepository notes;
 private final ComplaintRepository complaints;private final RentalOrderRepository orders;
 private final ChatService chat;private final NotificationService notifications;private final MessageRepository messages;
 public void admin(User user){if(user==null||user.getRole()!=Role.ADMIN||user.getAccountStatus()!=AccountStatus.ACTIVE)throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
 @Transactional public Conversation openSupport(User requester,User actor){
  if(actor.getRole()==Role.ADMIN){admin(actor);users.findByIdForChatUpdate(actor.getId()).orElseThrow();
   if(audit.countByChangedByAndCreatedAtAfter(actor,LocalDateTime.now().minusMinutes(1))>=10)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Tối đa 10 thao tác liên hệ/phân công mỗi phút.");}
  else if(!actor.getId().equals(requester.getId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  requester=users.findByIdForChatUpdate(requester.getId()).orElseThrow();
  if((requester.getRole()!=Role.CUSTOMER&&requester.getRole()!=Role.PROVIDER)||requester.getAccountStatus()!=AccountStatus.ACTIVE)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  var existing=conversations.findBySupportKey("requester:"+requester.getId());
  Conversation c;
  if(existing.isPresent())c=conversations.findByIdForUpdate(existing.get().getId()).orElseThrow();
  else {c=conversations.save(Conversation.builder().customer(requester.getRole()==Role.CUSTOMER?requester:null).provider(requester.getRole()==Role.PROVIDER?requester:null)
    .supportConversation(true).supportKey("requester:"+requester.getId()).build());
    notifications.notifyAdmins("SUPPORT_REQUEST","Yêu cầu hỗ trợ mới",requester.getFullName()+" mở yêu cầu hỗ trợ.","CONVERSATION",c.getId(),"/admin/support");}
  c.setSupportStatus("OPEN");
  if(actor.getRole()==Role.ADMIN){if(c.getSupportAdmin()!=null&&!c.getSupportAdmin().getId().equals(actor.getId()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Hội thoại đang do Admin khác phụ trách.");
    chat.claimSupport(c.getId(),actor);audit.save(SupportAssignmentHistory.builder().conversation(c).changedBy(actor).assignedAdmin(actor).action("CONTACTED").build());}
  return c;
 }
 @Transactional public Conversation openPrivate(User customer,User provider){
  users.findByIdForChatUpdate(customer.getId()).orElseThrow();
  return conversations.findByCustomerAndProvider(customer,provider).orElseGet(()->conversations.save(Conversation.builder().customer(customer).provider(provider).build()));
 }
 @Transactional(readOnly=true) public java.util.List<SupportRow> search(User admin,String q,String status,Long assignee,LocalDate from,LocalDate to){
  admin(admin);String term=q==null?"":q.strip().toLowerCase(Locale.ROOT);
  return conversations.findBySupportConversationTrueOrderByLastMessageAtDesc().stream().filter(c->chat.isParticipant(c,admin))
   .filter(c->status==null||status.isBlank()||status.equals(c.getSupportStatus()))
   .filter(c->assignee==null||(assignee==0?c.getSupportAdmin()==null:c.getSupportAdmin()!=null&&assignee.equals(c.getSupportAdmin().getId())))
   .filter(c->from==null||!c.getLastMessageAt().isBefore(from.atStartOfDay())).filter(c->to==null||c.getLastMessageAt().isBefore(to.plusDays(1).atStartOfDay()))
   .filter(c->{var u=c.getCustomer()!=null?c.getCustomer():c.getProvider();return term.isEmpty()||u.getFullName().toLowerCase(Locale.ROOT).contains(term)||u.getEmail().toLowerCase(Locale.ROOT).contains(term)
       ||term.equals(c.getId().toString())||c.getOrder()!=null&&term.equals(c.getOrder().getId().toString());})
   .limit(200).map(c->new SupportRow(c,messages.findTopByConversationOrderBySentAtDesc(c).map(Message::getContent).orElse(""),chat.unreadCount(c,admin))).toList();
 }
 @Transactional public void note(Long id,User admin,String content){var c=managed(id,admin);if(content==null||content.isBlank()||content.length()>2000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ghi chú phải có 1–2000 ký tự.");notes.save(SupportNote.builder().conversation(c).admin(admin).content(content.strip()).build());}
 @Transactional(readOnly=true) public java.util.List<NoteRow> notes(Long id,User admin){managed(id,admin);return notes.findByConversationIdOrderByIdAsc(id).stream().map(n->new NoteRow(n.getId(),n.getAdmin().getFullName(),n.getContent(),n.getCreatedAt())).toList();}
 @Transactional public void update(Long id,User admin,String status,Long orderId,Long complaintId){var c=managed(id,admin);
  if(!Set.of("OPEN","CLOSED").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  User requester=c.getCustomer()!=null?c.getCustomer():c.getProvider();
  if(orderId!=null){var o=orders.findById(orderId).orElseThrow();if(!o.getCustomer().getId().equals(requester.getId())&&(o.getProvider()==null||!o.getProvider().getId().equals(requester.getId())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Đơn không thuộc người yêu cầu.");c.setOrder(o);}
  if(complaintId!=null){var complaint=complaints.findById(complaintId).orElseThrow();var reporter=complaint.getReporter()!=null?complaint.getReporter():complaint.getCustomer();
   if(reporter==null||!reporter.getId().equals(requester.getId())||complaint.getHandledBy()!=null&&!complaint.getHandledBy().getId().equals(admin.getId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được liên kết khiếu nại này.");c.setLinkedComplaint(complaint);}
  c.setSupportStatus(status);audit.save(SupportAssignmentHistory.builder().conversation(c).changedBy(admin).assignedAdmin(c.getSupportAdmin()).action(status.equals("CLOSED")?"CLOSED":"UPDATED").build());
 }
 private Conversation managed(Long id,User admin){admin(admin);var c=chat.requireParticipant(id,admin);if(!Boolean.TRUE.equals(c.getSupportConversation()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return c;}
 public record SupportRow(Conversation conversation,String lastContent,long unreadCount){}
 public record NoteRow(Long id,String admin,String content,LocalDateTime createdAt){}
}
