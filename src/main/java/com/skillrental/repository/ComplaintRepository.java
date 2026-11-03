package com.skillrental.repository;

import com.skillrental.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint,Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Complaint c where c.id = :id")
    java.util.Optional<Complaint> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

}
