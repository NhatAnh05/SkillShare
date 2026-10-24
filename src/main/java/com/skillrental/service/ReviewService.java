package com.skillrental.service;

import com.skillrental.dto.*;
import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor public class ReviewService {
    private final ReviewRepository reviews;
    private final ComplaintRepository complaints;
    private final ProviderProfileRepository providers;
    @Transactional public Review save(RentalOrder o,User u,ReviewForm f) {
        if(o.getStatus()!=OrderStatus.COMPLETED)throw new IllegalArgumentException("Chỉ đánh giá đơn đã hoàn thành");
        var r=reviews.findByOrder(o).orElse(Review.builder().order(o).customer(u).provider(o.getProvider()).createdAt(LocalDateTime.now()).build());
        r.setRating(f.getRating());
        r.setComment(f.getComment());
        var saved=reviews.save(r);
        providers.findByUser(o.getProvider()).ifPresent(p-> {
            var all=reviews.findByProviderOrderByIdDesc(o.getProvider());p.setRating(Math.round(all.stream().mapToInt(Review::getRating).average().orElse(p.getRating())*10.0)/10.0);p.setReviewCount(all.size());providers.save(p);
        }
        );
        return saved;
    }
    @Transactional public void complain(RentalOrder o,User u,ComplaintForm f) {
        if (o == null || u == null || o.getCustomer() == null || o.getProvider() == null) {
            throw new IllegalArgumentException("Không tìm thấy hai bên của đơn hàng.");
        }
        boolean isCustomer = o.getCustomer().getId().equals(u.getId());
        boolean isProvider = o.getProvider().getId().equals(u.getId());
        if (!isCustomer && !isProvider) throw new IllegalArgumentException("Bạn không có quyền báo cáo đơn hàng này.");
        User target = isCustomer ? o.getProvider() : o.getCustomer();
        complaints.save(Complaint.builder()
                .customer(o.getCustomer())
                .reporter(u)
                .reportedUser(target)
                .order(o)
                .complaintType(isCustomer ? ComplaintType.PROVIDER : ComplaintType.USER)
                .subject(f.getSubject().trim())
                .description(f.getDescription().trim())
                .status(ComplaintStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
