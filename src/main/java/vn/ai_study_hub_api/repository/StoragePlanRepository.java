package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.StoragePlanEntity;

@Repository
public interface StoragePlanRepository extends JpaRepository<StoragePlanEntity, Integer> {
}
