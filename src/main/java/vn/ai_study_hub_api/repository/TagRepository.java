package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.TagEntity;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, Integer> {
}
