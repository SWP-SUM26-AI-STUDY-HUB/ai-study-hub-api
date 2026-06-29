package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.DocumentChunkEntity;
import vn.ai_study_hub_api.repository.projection.ChunkContentProjection;

import java.util.List;
import java.util.UUID;

/**
 * Read-only access to {@code document_chunks} (owned/indexed by the RAG service).
 *
 * <p>Exposes only a content projection query for the moderation service. Never write from this
 * side — extract / embed / delete stay with RAG.
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    /**
     * Returns the chunk text of a document, ordered by chunk index. Includes both embedded
     * (already indexed, e.g. a private-turned-public doc) and pending (embedding NULL, e.g. a
     * freshly-extracted public doc) chunks — moderation must cover all extracted content.
     */
    @Query(value = "SELECT chunk_index AS chunkIndex, content, page_number AS pageNumber "
            + "FROM document_chunks WHERE document_id = :id ORDER BY chunk_index",
            nativeQuery = true)
    List<ChunkContentProjection> findChunkContentsByDocumentId(@Param("id") UUID documentId);
}
