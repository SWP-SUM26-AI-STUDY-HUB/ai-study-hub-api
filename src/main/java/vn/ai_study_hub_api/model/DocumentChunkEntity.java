package vn.ai_study_hub_api.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/**
 * Read-only view of the {@code document_chunks} table.
 *
 * <p>The {@code document_chunks} table is <b>owned by the RAG service</b> (it performs extract /
 * embed / delete). This entity exists only as the JPA type anchor for
 * {@link vn.ai_study_hub_api.repository.DocumentChunkRepository}'s projection query.
 *
 * <p>{@code @Immutable} + no {@code save()} usage on the repository means this side never writes
 * the table — every write stays with RAG. The moderation service reads chunk content via
 * {@link vn.ai_study_hub_api.repository.projection.ChunkContentProjection} (no HTTP hop to RAG).
 *
 * <p>The {@code embedding} (vector) and {@code metadata} (jsonb) columns are intentionally not
 * mapped: this side only needs text content for moderation.
 */
@Entity
@Immutable
@Table(name = "document_chunks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunkEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    private String content;

    @Column(name = "page_number")
    private Integer pageNumber;
}
