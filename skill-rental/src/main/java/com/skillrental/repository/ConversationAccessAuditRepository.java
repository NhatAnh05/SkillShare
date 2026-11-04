package com.skillrental.repository;
import com.skillrental.entity.ConversationAccessAudit;import org.springframework.data.jpa.repository.JpaRepository;
public interface ConversationAccessAuditRepository extends JpaRepository<ConversationAccessAudit,Long>{java.util.List<ConversationAccessAudit> findByGrantIdOrderByIdAsc(Long id);}
