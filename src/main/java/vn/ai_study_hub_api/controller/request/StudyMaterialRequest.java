package vn.ai_study_hub_api.controller.request;

import lombok.Data;

import java.util.UUID;

/**
 * Request body for quiz / flashcard generation.
 *
 * <p>Generation is always document-scoped (unlike {@code /chat}, which allows a null
 * documentId to query all of the user's documents), so {@code documentId} is required.
 * {@code count} is clamped by the RAG service (quiz 5-20, flashcard 5-30); a null count
 * uses the per-type default. {@code focus} optionally scopes generation to a topic and is
 * injection-guarded by the RAG service.</p>
 */
@Data
public class StudyMaterialRequest {

    private UUID documentId;

    /**
     * Optional chat session to attach this generation to. When null, a new session is created so
     * the quiz/flashcard shows up in chat history. When set, the generation is appended to that
     * session (must be owned by the caller).
     */
    private UUID sessionId;

    private Integer count;

    private String focus;
}
