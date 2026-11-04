package com.skillrental.service;
import com.skillrental.entity.*;import com.skillrental.repository.*;import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;import org.springframework.web.server.ResponseStatusException;import java.time.*;import java.util.*;
@Service @RequiredArgsConstructor
public class ConversationReviewService {
 private final ConversationReviewGrantRepository grants;private final ConversationAccessAuditRepository audits;
 private final ConversationRepository conversations;private final ComplaintRepository complaints;private final MessageRepository messages;
 private final UserRepository users;private final NotificationService notifications;private final WorkDocumentRepository documents;private final SupportManagementService support;
 @Value("${skillshare.support.review-authorizers:}") private String authorizers;
 public boolean canApprove(User actor){return actor.getRole()==Role.ADMIN&&actor.getAccountStatus()==AccountStatus.ACTIVE&&Arrays.stream(authorizers.split(",")).anyMatch(e->e.strip().equalsIgnoreCase(actor.getEmail()));}
 private String reason(String value){if(value==null||value.isBlank()||value.length()>2000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cần lý do có 1–2000 ký tự.");return value.strip();}
 @Transactional public ConversationReviewGrant request(User admin,Long conversationId,Long complaintId,String reason,LocalDateTime from,LocalDateTime to){
  support.admin(admin);var c=conversations.findById(conversationId).orElseThrow();var complaint=complaints.findById(complaintId).orElseThrow();
  var o=complaint.getOrder();if(Boolean.TRUE.equals(c.getSupportConversation())||o==null||c.getCustomer()==null||c.getProvider()==null||!c.getCustomer().getId().equals(o.getCustomer().getId())||o.getProvider()==null||!c.getProvider().getId().equals(o.getProvider().getId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Hội thoại không thuộc hai bên của đơn đang khiếu nại.");
  if(complaint.getHandledBy()==null||!complaint.getHandledBy().getId().equals(admin.getId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ Admin được phân công khiếu nại mới được yêu cầu quyền xem.");
  if(from==null||to==null||from.isAfter(to)||Duration.between(from,to).compareTo(Duration.ofDays(31))>0||to.isAfter(LocalDateTime.now().plusMinutes(1)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Phạm vi tối đa 31 ngày và không ở tương lai.");
  var grant=grants.save(ConversationReviewGrant.builder().conversation(c).complaint(complaint).requester(admin).reason(reason(reason)).fromTime(from).toTime(to).build());
  log(grant,admin,"REQUEST",grant.getReason(),"conversation="+c.getId()+"; from="+from+"; to="+to);
  users.findByRole(Role.ADMIN).stream().filter(this::canApprove).filter(u->!u.getId().equals(admin.getId())).forEach(u->notifications.create(u,admin,"CHAT_REVIEW_REQUEST","Yêu cầu duyệt quyền xem bằng chứng",grant.getReason(),"REVIEW_GRANT",grant.getId(),"/admin/support-reviews"));return grant;
 }
 @Transactional public void decide(Long id,User actor,boolean approve,boolean exportAllowed,String note){
  support.admin(actor);if(!canApprove(actor))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chưa được cấp quyền duyệt truy cập chat riêng.");
  var g=grants.findForUpdate(id).orElseThrow();if(g.getRequester().getId().equals(actor.getId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được tự duyệt.");
  if(!"PENDING".equals(g.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT);
  g.setApprover(actor);g.setStatus(approve?"APPROVED":"REJECTED");g.setExportAllowed(approve&&exportAllowed);g.setExpiresAt(approve?LocalDateTime.now().plusHours(2):null);
  log(g,actor,approve?"APPROVE":"REJECT",reason(note),"export="+g.isExportAllowed());
  notifications.create(g.getRequester(),actor,"CHAT_REVIEW_DECISION",approve?"Quyền xem bằng chứng đã được duyệt":"Yêu cầu xem bằng chứng bị từ chối",note.strip(),"REVIEW_GRANT",g.getId(),"/admin/support-reviews");
 }
 @Transactional(readOnly=true) public List<GrantRow> list(User actor){support.admin(actor);return grants.findTop100ByOrderByIdDesc().stream().filter(g->canApprove(actor)||g.getRequester().getId().equals(actor.getId())).map(g->new GrantRow(g.getId(),g.getConversation().getId(),g.getComplaint().getId(),g.getRequester().getFullName(),g.getReason(),g.getStatus(),g.getFromTime(),g.getToTime(),g.getExpiresAt(),g.isExportAllowed())).toList();}
 @Transactional public List<Map<String,Object>> read(Long id,User actor,long after,boolean export,String purpose){
  var g=requireGrant(id,actor);if(export&&!g.isExportAllowed())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Quyền này không cho xuất dữ liệu.");
  var rows=messages.findTop200ByConversationAndSentAtBetweenAndIdGreaterThanOrderByIdAsc(g.getConversation(),g.getFromTime(),g.getToTime(),Math.max(0,after));
  log(g,actor,export?"EXPORT":"VIEW",export?reason(purpose):g.getReason(),"conversation="+g.getConversation().getId()+"; after="+after+"; count="+rows.size()+"; from="+g.getFromTime()+"; to="+g.getToTime());
  return rows.stream().map(m->Map.<String,Object>of("id",m.getId(),"sender",m.getSender().getFullName(),"content",m.getContent(),"sentAt",m.getSentAt().toString())).toList();
 }
 @Transactional public List<Map<String,Object>> documents(Long id,User actor){return documents(id,actor,0);}
 @Transactional public List<Map<String,Object>> documents(Long id,User actor,long after){var g=requireGrant(id,actor);var rows=documents.findTop200ByConversationIdAndCreatedAtBetweenAndIdGreaterThanOrderByIdAsc(g.getConversation().getId(),g.getFromTime(),g.getToTime(),Math.max(0,after));log(g,actor,"VIEW_DOCUMENTS",g.getReason(),"metadata; after="+after+"; count="+rows.size());return rows.stream().map(d->Map.<String,Object>of("id",d.getId(),"sender",d.getSender().getFullName(),"description",d.getDescription(),"name",d.getOriginalName()==null?"":d.getOriginalName(),"url",d.getUrl()==null?"":d.getUrl(),"createdAt",d.getCreatedAt().toString(),"downloadAllowed",g.isExportAllowed()&&d.getStorageKey()!=null)).toList();}
 @Transactional public WorkDocument download(Long id,Long documentId,User actor){var g=requireGrant(id,actor);if(!g.isExportAllowed())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Cần quyền xuất tệp riêng.");var d=documents.findById(documentId).orElseThrow();if(d.getConversation()==null||!d.getConversation().getId().equals(g.getConversation().getId())||d.getCreatedAt().isBefore(g.getFromTime())||d.getCreatedAt().isAfter(g.getToTime())||d.getStorageKey()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN);log(g,actor,"DOWNLOAD",g.getReason(),"document="+documentId);return d;}
 @Transactional(readOnly=true) public ConversationReviewGrant requireGrant(Long id,User actor){support.admin(actor);var g=grants.findById(id).orElseThrow();if(g.getComplaint().getHandledBy()==null||!g.getComplaint().getHandledBy().getId().equals(actor.getId())||!g.getRequester().getId().equals(actor.getId())||!"APPROVED".equals(g.getStatus())||g.getExpiresAt()==null||!g.getExpiresAt().isAfter(LocalDateTime.now()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Quyền chưa được duyệt hoặc đã hết hạn.");return g;}
 @Transactional(readOnly=true) public List<Map<String,Object>> audit(Long id,User actor){support.admin(actor);var g=grants.findById(id).orElseThrow();if(!canApprove(actor)&&!g.getRequester().getId().equals(actor.getId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return audits.findByGrantIdOrderByIdAsc(id).stream().map(a->Map.<String,Object>of("actor",a.getActor().getFullName(),"action",a.getAction(),"reason",a.getReason(),"scope",a.getDataScope(),"time",a.getCreatedAt().toString())).toList();}
 private void log(ConversationReviewGrant g,User actor,String action,String reason,String scope){audits.save(ConversationAccessAudit.builder().grant(g).actor(actor).action(action).reason(reason).dataScope(scope).build());}
 public record GrantRow(Long id,Long conversationId,Long complaintId,String requester,String reason,String status,LocalDateTime fromTime,LocalDateTime toTime,LocalDateTime expiresAt,boolean exportAllowed){}
}
