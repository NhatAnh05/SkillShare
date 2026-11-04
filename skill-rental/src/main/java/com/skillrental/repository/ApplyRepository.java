package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApplyRepository extends JpaRepository<Apply,Long> {
    List<Apply> findByRequestOrderByAppliedAtDesc(ServiceRequest request);
}
