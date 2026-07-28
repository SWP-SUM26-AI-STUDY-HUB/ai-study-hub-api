package vn.ai_study_hub_api.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.repository.projection.TrendingStatsProjection;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrendingDocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    @Query("SELECT d FROM DocumentEntity d " +
            "WHERE d.visibility = vn.ai_study_hub_api.model.DocumentVisibility.PUBLIC " +
            "  AND d.status = vn.ai_study_hub_api.model.DocumentStatus.COMPLETED " +
            "  AND d.deletedAt IS NULL " +
            "ORDER BY d.downloadCount DESC, d.createdAt DESC")
    List<DocumentEntity> findTrendingDocuments(Pageable pageable);

    @Query(value = "SELECT document_id as documentId, COALESCE(AVG(rating), 0.0) as averageRating, COUNT(id) as reviewCount " +
            "FROM reviews WHERE document_id IN :documentIds GROUP BY document_id",
            nativeQuery = true)
    List<TrendingStatsProjection> findStatsForDocuments(@Param("documentIds") List<UUID> documentIds);
    /**
     * Fetches documents (among {@code docIds}) owned by {@code userId} with their tags eagerly
     * loaded. Used to enrich a cached trending page with the owner's PRIVATE tags — only the docs
     * the caller actually owns are touched, so the hot path stays a no-op for non-owners.
     */
    @Query("SELECT DISTINCT d FROM DocumentEntity d LEFT JOIN FETCH d.tags " +
            "WHERE d.id IN :docIds AND d.uploader.id = :userId")
    List<DocumentEntity> findOwnedDocumentsWithTags(@Param("docIds") List<UUID> docIds,
                                                    @Param("userId") UUID userId);

}
