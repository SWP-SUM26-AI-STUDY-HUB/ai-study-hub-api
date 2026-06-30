package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.TagVisibility;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, Integer> {
    Optional<TagEntity> findByLabel(String label);
    Optional<TagEntity> findByLabelIgnoreCaseAndVisibility(String label, TagVisibility visibility);
    Optional<TagEntity> findByLabelIgnoreCaseAndVisibilityAndCreatedBy_Id(String label, TagVisibility visibility, UUID createdById);
    List<TagEntity> findByLabelContainingIgnoreCase(String label);

    @Query("SELECT t FROM TagEntity t WHERE LOWER(t.label) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "AND (t.visibility = vn.ai_study_hub_api.model.TagVisibility.PUBLIC OR t.visibility IS NULL " +
           "     OR (t.visibility = vn.ai_study_hub_api.model.TagVisibility.PRIVATE AND t.createdBy.id = :userId))")
    List<TagEntity> searchTagsForUser(@Param("keyword") String keyword, @Param("userId") UUID userId);

    @Query("SELECT t FROM TagEntity t WHERE LOWER(t.label) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "AND (t.visibility = vn.ai_study_hub_api.model.TagVisibility.PUBLIC OR t.visibility IS NULL)")
    List<TagEntity> searchPublicTags(@Param("keyword") String keyword);
    @Query("SELECT t FROM TagEntity t WHERE LOWER(t.label) = LOWER(:label) " +
           "AND (t.visibility = vn.ai_study_hub_api.model.TagVisibility.PUBLIC OR t.visibility IS NULL)")
    Optional<TagEntity> findPublicOrLegacyTag(@Param("label") String label);
}




