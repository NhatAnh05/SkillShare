package com.skillrental.repository;
import com.skillrental.entity.SupportNote;import org.springframework.data.jpa.repository.JpaRepository;
public interface SupportNoteRepository extends JpaRepository<SupportNote,Long>{java.util.List<SupportNote> findByConversationIdOrderByIdAsc(Long id);}
