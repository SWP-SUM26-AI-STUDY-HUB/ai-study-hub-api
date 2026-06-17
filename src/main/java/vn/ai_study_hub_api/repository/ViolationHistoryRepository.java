package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.ViolationHistoryEntity;
import java.util.UUID;

@Repository
public interface ViolationHistoryRepository extends JpaRepository<ViolationHistoryEntity, UUID> {
    long countByUserIdAndStatus(UUID userId, String status);
}
