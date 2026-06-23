package vn.ai_study_hub_api.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Client for the external RAG chatbot service (FastAPI).
 *
 * Wire contract (mirrors main.py {@code POST /api/v1/chat}):
 *   request  : { "query": str, "user_id": str, "document_id": str|null }
 *   response : { "success": bool, "message": str,
 *                "data": { "llm_response": str, "debug": obj }, "timestamp": str }
 */
public interface ChatbotClient {

    /**
     * Calls the chatbot {@code /api/v1/chat} endpoint (blocking JSON).
     *
     * @param query      the user question
     * @param userId     authenticated user id (sent as string; chatbot uses it when documentId is null
     *                   to scope retrieval to all of the user's documents)
     * @param documentId specific document to query, or null to query all of the user's documents
     * @return parsed chatbot response
     */
    ChatbotResponse chat(String query, UUID userId, UUID documentId);

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    class ChatbotRequest {
        @JsonProperty("query")
        private String query;

        @JsonProperty("user_id")
        private String userId;

        @JsonProperty("document_id")
        private String documentId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    class ChatbotResponse {
        @JsonProperty("success")
        private boolean success;

        @JsonProperty("message")
        private String message;

        @JsonProperty("data")
        private ResponseData data;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    class ResponseData {
        @JsonProperty("llm_response")
        private String llmResponse;

        @JsonProperty("debug")
        private JsonNode debug;
    }
}
