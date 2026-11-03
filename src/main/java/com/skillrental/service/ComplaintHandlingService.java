package com.skillrental.service;
import com.skillrental.entity.*;import com.skillrental.repository.*;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.server.ResponseStatusException;import org.springframework.http.HttpStatus;import java.time.LocalDateTime;
@Service @RequiredArgsConstructor public class ComplaintHandlingService {
 private final ComplaintRepository complaints;private final SupportManagementService support;private final NotificationService notifications;
 @Transactional public void claim(Long id,User admin){support.admin(admin);var c=complaints.findByIdForUpdate(id).orElseThrow();if(c.getHandledBy()!=null&&!c.getHandledBy().getId().equals(admin.getId()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Khiếu nại đang do Admin khác xử lý.");c.setHandledBy(admin);if(c.getStatus()==ComplaintStatus.OPEN)c.setStatus(ComplaintStatus.PROCESSING);}
 @Transactional public void decide(Long id,User admin,ComplaintStatus status,String note){claim(id,admin);var c=complaints.findByIdForUpdate(id).orElseThrow();if(note==null||note.isBlank()||note.length()>1500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cần phản hồi có 1–1500 ký tự.");if(status!=ComplaintStatus.RESOLVED&&status!=ComplaintStatus.REJECTED)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  if(c.getStatus()==status&&note.strip().equals(c.getResolutionNotes()))return;
  if(c.getStatus()==ComplaintStatus.RESOLVED||c.getStatus()==ComplaintStatus.REJECTED)throw new ResponseStatusException(HttpStatus.CONFLICT,"Khiếu nại đã đóng.");
  c.setStatus(status);c.setResolutionNotes(note.strip());c.setResolvedAt(LocalDateTime.now());
  User reporter=c.getReporter()!=null?c.getReporter():c.getCustomer();if(reporter!=null)notifications.create(reporter,admin,"COMPLAINT_RESPONSE","Admin phản hồi khiếu nại #"+id,note.strip(),"COMPLAINT",id,"/complaints/"+id);
 }
 @Transactional(readOnly=true) public Complaint view(Long id,User actor){var c=complaints.findById(id).orElseThrow();var reporter=c.getReporter()!=null?c.getReporter():c.getCustomer();if(actor.getRole()!=Role.ADMIN&&(reporter==null||!reporter.getId().equals(actor.getId())))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return c;}
}
