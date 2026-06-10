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

    @Query(value = "SELECT d.id, d.uploader_id, d.title, d.file_url, d.file_type, d.file_size_bytes, " +
            "UPPER(d.status::text) as status, UPPER(d.visibility::text) as visibility, " +
            "d.link_share, d.created_at, d.updated_at, d.deleted_at, d.summary, d.description FROM documents d " +
            "LEFT JOIN (SELECT document_id, AVG(rating) as avg_rating, COUNT(id) as review_count " +
            "           FROM reviews GROUP BY document_id) r ON d.id = r.document_id " +
            "WHERE cast(d.visibility as text) = 'public' " +
            "  AND cast(d.status as text) = 'completed' " +
            "  AND d.deleted_at IS NULL " +
            "ORDER BY COALESCE(r.avg_rating, 0.0) DESC, COALESCE(r.review_count, 0) DESC, d.created_at DESC",
            countQuery = "SELECT count(*) FROM documents d " +
            "WHERE cast(d.visibility as text) = 'public' " +
            "  AND cast(d.status as text) = 'completed' " +
            "  AND d.deleted_at IS NULL",
            nativeQuery = true)
    Page<DocumentEntity> findTrendingDocuments(Pageable pageable);

    @Query(value = "SELECT document_id as documentId, COALESCE(AVG(rating), 0.0) as averageRating, COUNT(id) as reviewCount " +
            "FROM reviews WHERE document_id IN :documentIds GROUP BY document_id",
            nativeQuery = true)
    List<TrendingStatsProjection> findStatsForDocuments(@Param("documentIds") List<UUID> documentIds);
}
