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

@Repository
public interface TagRepository extends JpaRepository<TagEntity, Integer> {
    Optional<TagEntity> findByLabel(String label);
    Optional<TagEntity> findByLabelIgnoreCaseAndVisibility(String label, TagVisibility visibility);
    List<TagEntity> findAllByLabelIgnoreCaseAndVisibility(String label, TagVisibility visibility);
    List<TagEntity> findByLabelContainingIgnoreCase(String label);

    @Modifying
    @Query(value = "UPDATE document_tags SET tag_id = :newTagId WHERE tag_id = :oldTagId AND document_id NOT IN (SELECT document_id FROM document_tags WHERE tag_id = :newTagId)", nativeQuery = true)
    void reassignDocumentTags(@Param("oldTagId") Integer oldTagId, @Param("newTagId") Integer newTagId);

    @Modifying
    @Query(value = "DELETE FROM document_tags WHERE tag_id = :oldTagId", nativeQuery = true)
    void deleteDocumentTagsByTagId(@Param("oldTagId") Integer oldTagId);
}


