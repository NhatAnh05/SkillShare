package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ReviewRepository extends JpaRepository<Review,Long> {
    Optional<Review> findByOrder(RentalOrder order);
    List<Review> findByProviderOrderByIdDesc(User provider);
}
