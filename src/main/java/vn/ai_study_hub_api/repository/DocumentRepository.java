package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    @Query("SELECT d FROM DocumentEntity d JOIN FETCH d.uploader WHERE d.id = :id")
    Optional<DocumentEntity> findByIdWithUploader(@Param("id") UUID id);

    @Query("SELECT d FROM DocumentEntity d WHERE d.uploader.id = :uploaderId AND d.deletedAt IS NULL")
    List<DocumentEntity> findActiveDocumentsByUploaderId(@Param("uploaderId") UUID uploaderId);

    /**
     * Search public documents by keyword across title, description, summary, and tag labels.
     * Filters: visibility = PUBLIC, status = COMPLETED, deleted_at IS NULL.
     * Uses LEFT JOIN on tags for keyword matching in tag labels.
     */
    @Query("SELECT DISTINCT d FROM DocumentEntity d " +
           "LEFT JOIN d.tags t " +
           "WHERE d.visibility = :visibility " +
           "AND d.status = :status " +
           "AND d.deletedAt IS NULL " +
           "AND (LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(d.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(d.summary) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "  OR LOWER(t.label) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<DocumentEntity> searchPublicDocuments(
            @Param("keyword") String keyword,
            @Param("visibility") DocumentVisibility visibility,
            @Param("status") DocumentStatus status);
}
