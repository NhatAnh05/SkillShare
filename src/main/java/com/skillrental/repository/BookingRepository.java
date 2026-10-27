package com.skillrental.repository;

import com.skillrental.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking,Long> {
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
}
