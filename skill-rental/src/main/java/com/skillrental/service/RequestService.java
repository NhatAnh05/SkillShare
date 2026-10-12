package com.skillrental.service;

import com.skillrental.dto.ServiceRequestForm;
import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor public class RequestService {
    private final ServiceRequestRepository requests;
    private final ApplyRepository applies;
    public List<ServiceRequest> list(User u) {
        return requests.findByCustomerOrderByIdDesc(u);
    }

    /** Public-to-authenticated marketplace list: only requests still accepting providers. */
    public List<ServiceRequest> browseOpenRequests() {
        return requests.findByStatusOrderByIdDesc(RequestStatus.OPEN);
    }

    /** Home preview: keep the page compact while allowing the full list on the browse page. */
    public List<ServiceRequest> recentOpenRequests(int limit) {
        int safeLimit = Math.max(0, limit);
        return browseOpenRequests().stream().limit(safeLimit).toList();
    }

    /** Counts applications in one pass so request cards can display accurate totals. */
    public Map<Long, Long> applicationCounts(List<ServiceRequest> requestList) {
        Set<Long> ids = requestList.stream().map(ServiceRequest::getId).collect(Collectors.toSet());
        return applies.findAll().stream()
                .filter(a -> a.getRequest() != null && ids.contains(a.getRequest().getId()))
                .collect(Collectors.groupingBy(a -> a.getRequest().getId(), Collectors.counting()));
    }
    @Transactional public ServiceRequest create(User u,ServiceRequestForm f) {
        return requests.save(ServiceRequest.builder().customer(u).title(f.getTitle()).requiredSkills(f.getRequiredSkills()).description(f.getDescription()).budget(f.getBudget()).deadline(f.getDeadline()).status(RequestStatus.OPEN).build());
    }
    @Transactional
    public void updateOpenRequest(ServiceRequest request, ServiceRequestForm form) {
        if (request.getStatus() != RequestStatus.OPEN) {
            throw new IllegalArgumentException("Chỉ sửa được Request đang mở");
        }
        request.setTitle(form.getTitle());
        request.setRequiredSkills(form.getRequiredSkills());
        request.setDescription(form.getDescription());
        request.setBudget(form.getBudget());
        request.setDeadline(form.getDeadline());
        requests.save(request);
    }

    public ServiceRequest owned(Long id,User u) {
        var r=requests.findById(id).orElseThrow();
        if(!r.getCustomer().getId().equals(u.getId()))throw new IllegalArgumentException("Không có quyền");
        return r;
    }
    public List<Apply> applies(ServiceRequest r) {
        return applies.findByRequestOrderByAppliedAtDesc(r);
    }
    @Transactional public void choose(ServiceRequest r,Long id) {
        var selected=applies.findById(id).orElseThrow();
        if (r.getStatus() != RequestStatus.OPEN || !selected.getRequest().getId().equals(r.getId())) {
            throw new IllegalArgumentException("Apply không hợp lệ cho Request này");
        }
        var all=applies.findByRequestOrderByAppliedAtDesc(r);
        all.forEach(a->a.setStatus(a.getId().equals(id)?ApplyStatus.ACCEPTED:ApplyStatus.REJECTED));
        applies.saveAll(all);
        r.setSelectedProvider(selected.getProvider());
        r.setStatus(RequestStatus.IN_PROGRESS);
        requests.save(r);
    }
}
