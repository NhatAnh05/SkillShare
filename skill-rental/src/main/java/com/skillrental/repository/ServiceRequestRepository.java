package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest,Long> {
    List<ServiceRequest> findByCustomerOrderByIdDesc(User customer);
    List<ServiceRequest> findByStatusOrderByIdDesc(RequestStatus status);
}
