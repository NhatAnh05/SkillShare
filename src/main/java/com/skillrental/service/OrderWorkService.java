package com.skillrental.service;
import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
@Service @RequiredArgsConstructor
public class OrderWorkService {
 private final RentalOrderRepository orders;
 private final OrderWorkUpdateRepository updates;
 private final OrderService access;
 private final NotificationService notifications;
 @Transactional(readOnly=true) public List<OrderWorkUpdate> history(Long id,User viewer){access.involved(id,viewer);return updates.findByOrderIdOrderByIdDesc(id);}
 @Transactional public void update(Long id,User actor,int percent,String description,LocalDateTime eta){
  RentalOrder order=orders.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Không tìm thấy đơn hàng."));
  if(actor.getRole()!=Role.PROVIDER||order.getProvider()==null||!order.getProvider().getId().equals(actor.getId()))throw new IllegalArgumentException("Chỉ Provider phụ trách được cập nhật tiến độ.");
  if(order.getStatus()!=OrderStatus.PROCESSING||!Boolean.TRUE.equals(order.getProviderStarted())||!Boolean.TRUE.equals(order.getCustomerStarted()))throw new IllegalStateException("Hai bên cần xác nhận bắt đầu trước khi cập nhật tiến độ.");
  if(percent<0||percent>99)throw new IllegalArgumentException("Tiến độ từ 0 đến 99%; 100% chỉ được ghi nhận khi nộp sản phẩm.");
  if(description==null||description.isBlank()||description.strip().length()>2000)throw new IllegalArgumentException("Mô tả phần việc đã làm (tối đa 2000 ký tự).");
  if(eta==null||!eta.isAfter(LocalDateTime.now()))throw new IllegalArgumentException("Thời điểm dự kiến hoàn thành phải ở tương lai.");
  updates.save(OrderWorkUpdate.builder().order(order).provider(actor).description(description.strip()).progressPercent(percent).estimatedCompletionAt(eta).build());
  order.setProgressPercent(percent);order.setEstimatedCompletionAt(eta);orders.save(order);
  notifications.create(order.getCustomer(),actor,"WORK_PROGRESS","Provider đã cập nhật tiến độ",description.strip(),"ORDER",id,"/orders/"+id+"/progress");
 }
}
