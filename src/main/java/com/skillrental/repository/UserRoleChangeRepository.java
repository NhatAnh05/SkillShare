package com.skillrental.repository;
import com.skillrental.entity.UserRoleChange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface UserRoleChangeRepository extends JpaRepository<UserRoleChange,Long> {
 List<UserRoleChange> findTop20ByOrderByChangedAtDescIdDesc();
 long countByTargetId(Long targetId);
}
