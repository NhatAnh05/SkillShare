package com.skillrental.repository;
import com.skillrental.entity.WorkDocument;import org.springframework.data.jpa.repository.*;import org.springframework.data.domain.Pageable;
public interface WorkDocumentRepository extends JpaRepository<WorkDocument,Long>{
 java.util.List<WorkDocument> findTop200ByConversationIdAndCreatedAtBetweenAndIdGreaterThanOrderByIdAsc(Long id,java.time.LocalDateTime from,java.time.LocalDateTime to,Long after);
 java.util.List<WorkDocument> findByOrderIdOrderByIdDesc(Long id,Pageable page);
 java.util.List<WorkDocument> findByConversationIdOrderByIdDesc(Long id,Pageable page);
}
