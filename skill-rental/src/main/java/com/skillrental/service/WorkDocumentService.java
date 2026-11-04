package com.skillrental.service;
import com.skillrental.entity.*;import com.skillrental.repository.*;import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;import org.springframework.web.server.ResponseStatusException;import org.springframework.data.domain.PageRequest;import java.util.*;import java.net.URI;
@Service @RequiredArgsConstructor
public class WorkDocumentService {
 private final WorkDocumentRepository documents;private final OrderService orders;private final ChatService chat;private final PrivateFileStorage storage;private final NotificationService notifications;
 @Transactional public List<DocumentRow> upload(Long orderId,Long conversationId,User actor,String description,List<String> links,List<MultipartFile> files){
  var order=orderId==null?null:authorizedOrder(orderId,actor);var conversation=conversationId==null?null:authorizedConversation(conversationId,actor);
  if(conversation!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Chat chỉ dùng trao đổi. Hãy gửi yêu cầu/tài liệu hoặc bàn giao sản phẩm trong đơn hàng.");
  if((order==null)==(conversation==null))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  if(order!=null&&(order.getStatus()==OrderStatus.COMPLETED||order.getStatus()==OrderStatus.CANCELLED||order.getStatus()==OrderStatus.PENDING_PAYMENT_OTP))throw new ResponseStatusException(HttpStatus.CONFLICT,"Đơn đã đóng trao đổi tài liệu mới.");
  if(conversation!=null&&Boolean.TRUE.equals(conversation.getSupportConversation())&&"CLOSED".equals(conversation.getSupportStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Hãy mở lại yêu cầu hỗ trợ trước khi gửi tài liệu.");
  if(description==null||description.isBlank()||description.length()>2000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhập mô tả tài liệu (tối đa 2000 ký tự).");
  var uploads=files==null?List.<MultipartFile>of():files.stream().filter(f->f!=null&&!f.isEmpty()).toList();var urls=links==null?List.<String>of():links.stream().filter(l->l!=null&&!l.isBlank()).map(String::strip).toList();
  if(uploads.size()>5||urls.size()>10)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tối đa 5 tệp và 10 liên kết mỗi lần.");
  List<WorkDocument> rows=new ArrayList<>();
  for(String url:urls){URI uri;try{uri=URI.create(url);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Liên kết không hợp lệ.");}if(url.length()>1000||!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Liên kết chỉ hỗ trợ http/https hợp lệ.");rows.add(base(order,conversation,actor,description).url(url).build());}
  for(var file:uploads){var stored=storage.store(file);rows.add(base(order,conversation,actor,description).originalName(stored.name()).storageKey(stored.key()).fileSize(stored.size()).contentType(stored.type()).build());}
  if(rows.isEmpty())rows.add(base(order,conversation,actor,description).build());documents.saveAll(rows);
  if(conversation!=null)conversation.setLastMessageAt(java.time.LocalDateTime.now());
  String target=order!=null?"/orders/"+orderId+"/progress":"/messages?conversationId="+conversationId;
  String type=order!=null?"ORDER":"CONVERSATION";Long targetId=order!=null?orderId:conversationId;
  if(order!=null){User recipient=order.getCustomer().getId().equals(actor.getId())?order.getProvider():order.getCustomer();notifications.create(recipient,actor,"WORK_DOCUMENT","Tài liệu mới từ "+actor.getFullName(),description,type,targetId,target);}
  else {var c=conversation;if(Boolean.TRUE.equals(c.getSupportConversation())){if(actor.getRole()==Role.ADMIN)notifications.create(c.getCustomer()!=null?c.getCustomer():c.getProvider(),actor,"WORK_DOCUMENT","Tài liệu hỗ trợ mới",description,type,targetId,target);
    else if(c.getSupportAdmin()!=null)notifications.create(c.getSupportAdmin(),actor,"WORK_DOCUMENT","Tài liệu hỗ trợ mới",description,type,targetId,target);else notifications.notifyAdmins("WORK_DOCUMENT","Tài liệu hỗ trợ mới",description,type,targetId,"/admin/support");}
    else notifications.create(c.getCustomer().getId().equals(actor.getId())?c.getProvider():c.getCustomer(),actor,"WORK_DOCUMENT","Tài liệu mới từ "+actor.getFullName(),description,type,targetId,target);}
  return rows.stream().map(this::row).toList();
 }
 private RentalOrder authorizedOrder(Long id,User actor){try{return orders.involved(id,actor);}catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không tham gia đơn hàng này.");}}
 private Conversation authorizedConversation(Long id,User actor){try{return chat.requireParticipant(id,actor);}catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không tham gia hội thoại này.");}}
 private WorkDocument.WorkDocumentBuilder base(RentalOrder order,Conversation c,User actor,String description){return WorkDocument.builder().order(order).conversation(c).sender(actor).description(description.strip());}
 @Transactional(readOnly=true) public List<DocumentRow> list(Long orderId,Long conversationId,User actor,int page){if(orderId!=null)authorizedOrder(orderId,actor);else authorizedConversation(conversationId,actor);return (orderId!=null?documents.findByOrderIdOrderByIdDesc(orderId,PageRequest.of(Math.max(0,page),50)):documents.findByConversationIdOrderByIdDesc(conversationId,PageRequest.of(Math.max(0,page),50))).stream().map(this::row).toList();}
 @Transactional(readOnly=true) public WorkDocument require(Long id,User actor){var d=documents.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(d.getOrder()!=null)authorizedOrder(d.getOrder().getId(),actor);else authorizedConversation(d.getConversation().getId(),actor);if(d.getStorageKey()==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);return d;}
 private DocumentRow row(WorkDocument d){return new DocumentRow(d.getId(),d.getSender().getFullName(),d.getDescription(),d.getOriginalName(),d.getFileSize(),d.getUrl(),d.getCreatedAt()==null?java.time.LocalDateTime.now():d.getCreatedAt(),d.getStorageKey()==null?null:"/documents/"+d.getId()+"/download");}
 public record DocumentRow(Long id,String sender,String description,String name,Long size,String url,java.time.LocalDateTime createdAt,String downloadUrl){}
}
