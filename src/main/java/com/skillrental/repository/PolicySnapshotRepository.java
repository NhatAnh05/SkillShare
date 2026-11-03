package com.skillrental.repository;
import com.skillrental.entity.PolicySnapshot;import org.springframework.data.jpa.repository.*;import java.util.*;
public interface PolicySnapshotRepository extends JpaRepository<PolicySnapshot,Long>{
 Optional<PolicySnapshot> findByPolicyTypeAndPolicyVersion(String type,String version);
}
