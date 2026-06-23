package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.request.ChatRequest;
import vn.ai_study_hub_api.controller.response.ChatMessageResponse;
import vn.ai_study_hub_api.controller.response.ChatResponse;
import vn.ai_study_hub_api.controller.response.ChatSessionResponse;
import vn.ai_study_hub_api.controller.response.QuotaResponse;

import java.util.List;
import java.util.UUID;

public interface ChatService {

    /** Multi/single-document contextual chat (F-AI-01) and summarize (F-AI-02). */
    ChatResponse chat(ChatRequest req, UUID userId);

    /** List active chat sessions for the user (F-AI-03.1). */
    List<ChatSessionResponse> listSessions(UUID userId);

    /** Chronological message history for a session (F-AI-03.2). */
    List<ChatMessageResponse> getMessages(UUID sessionId, UUID userId);

    /** Rename a session (F-AI-03.3). */
    void renameSession(UUID sessionId, String title, UUID userId);

    /** Soft-delete a session (F-AI-03.3). */
    void deleteSession(UUID sessionId, UUID userId);

    /** Read-only daily quota snapshot (US-MON-02). */
    QuotaResponse getQuota(UUID userId);
}
