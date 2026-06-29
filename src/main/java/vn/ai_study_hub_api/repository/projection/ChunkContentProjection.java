package vn.ai_study_hub_api.repository.projection;

/**
 * Read-only projection of a document chunk's content for the moderation service.
 *
 * <p>Moderation reads chunks per-document (via
 * {@link vn.ai_study_hub_api.repository.DocumentChunkRepository#findChunkContentsByDocumentId}),
 * classifies each with the OpenAI Moderation API, and decides at the document level
 * (e.g. any severe category or a high violation ratio {@code ->} reject; otherwise approve).
 */
public interface ChunkContentProjection {
    Integer getChunkIndex();

    String getContent();

    Integer getPageNumber();
}
