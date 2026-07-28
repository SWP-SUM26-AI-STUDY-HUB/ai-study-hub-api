package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    @Query("SELECT d FROM DocumentEntity d JOIN FETCH d.uploader WHERE d.id = :id")
    Optional<DocumentEntity> findByIdWithUploader(@Param("id") UUID id);

    @Query("SELECT d FROM DocumentEntity d JOIN FETCH d.uploader WHERE d.linkShare = :linkShare")
    Optional<DocumentEntity> findByLinkShare(@Param("linkShare") String linkShare);
    @Query("SELECT d FROM DocumentEntity d WHERE d.uploader.id = :uploaderId AND d.deletedAt IS NULL")
    List<DocumentEntity> findActiveDocumentsByUploaderId(@Param("uploaderId") UUID uploaderId);

    Optional<DocumentEntity> findFirstByContentHashAndDeletedAtIsNull(String contentHash);

    @Query("SELECT d FROM DocumentEntity d WHERE d.status = :status AND d.visibility = :visibility AND d.deletedAt IS NULL")
    List<DocumentEntity> findPendingPublicDocuments(
            @Param("status") DocumentStatus status,
            @Param("visibility") DocumentVisibility visibility);

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
           "AND (CAST(function('unaccent', LOWER(d.title)) AS string) LIKE CAST(function('unaccent', LOWER(CONCAT('%', :keyword, '%'))) AS string) " +
           "  OR CAST(function('unaccent', LOWER(d.description)) AS string) LIKE CAST(function('unaccent', LOWER(CONCAT('%', :keyword, '%'))) AS string) " +
           "  OR CAST(function('unaccent', LOWER(d.summary)) AS string) LIKE CAST(function('unaccent', LOWER(CONCAT('%', :keyword, '%'))) AS string) " +
           "  OR ((t.visibility IS NULL OR t.visibility = vn.ai_study_hub_api.model.TagVisibility.PUBLIC) " +
           "      AND CAST(function('unaccent', LOWER(t.label)) AS string) LIKE CAST(function('unaccent', LOWER(CONCAT('%', :keyword, '%'))) AS string))))")
    List<DocumentEntity> searchPublicDocuments(
            @Param("keyword") String keyword,
            @Param("visibility") DocumentVisibility visibility,
            @Param("status") DocumentStatus status);

    @Query(value = "SELECT d.id FROM documents d " +
            "JOIN document_tags dt ON d.id = dt.document_id " +
            "LEFT JOIN reviews r ON d.id = r.document_id " +
            "WHERE dt.tag_id IN (:tagIds) " +
            "AND UPPER(d.status::text) = 'COMPLETED' " +
            "AND UPPER(d.visibility::text) = 'PUBLIC' " +
            "AND d.deleted_at IS NULL " +
            "GROUP BY d.id, d.created_at " +
            "ORDER BY COUNT(DISTINCT dt.tag_id) DESC, " +
            "COALESCE(AVG(r.rating), 0) DESC, " +
            "d.created_at DESC " +
            "LIMIT 100",
            nativeQuery = true)
    List<UUID> findRecommendedDocumentIds(@Param("tagIds") List<Integer> tagIds);

    @Query("SELECT d FROM DocumentEntity d WHERE d.uploader.id = :uploaderId AND d.visibility = :visibility AND d.status = :status AND d.deletedAt IS NULL")
    org.springframework.data.domain.Page<DocumentEntity> findPublicDocumentsByUploaderId(
            @Param("uploaderId") UUID uploaderId,
            @Param("visibility") DocumentVisibility visibility,
            @Param("status") DocumentStatus status,
            org.springframework.data.domain.Pageable pageable);
    @Query("SELECT d FROM DocumentEntity d WHERE d.deletedAt IS NOT NULL AND d.deletedAt < :cutoff")
    List<DocumentEntity> findSoftDeletedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT d FROM DocumentEntity d WHERE d.uploader.id = :uploaderId AND d.deletedAt IS NOT NULL ORDER BY d.deletedAt DESC")
    List<DocumentEntity> findSoftDeletedDocumentsByUploaderId(@Param("uploaderId") UUID uploaderId);

    @Modifying
    @Query(value = "DELETE FROM session_documents WHERE document_id = :documentId", nativeQuery = true)
    int deleteSessionDocumentsByDocumentId(@Param("documentId") UUID documentId);
}

