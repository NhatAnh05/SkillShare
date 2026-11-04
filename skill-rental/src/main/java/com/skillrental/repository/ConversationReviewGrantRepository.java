package com.skillrental.repository;
import com.skillrental.entity.ConversationReviewGrant;import org.springframework.data.jpa.repository.*;import java.util.*;
public interface ConversationReviewGrantRepository extends JpaRepository<ConversationReviewGrant,Long>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select g from ConversationReviewGrant g where g.id = :id")
 Optional<ConversationReviewGrant> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
 List<ConversationReviewGrant> findTop100ByOrderByIdDesc();
}
