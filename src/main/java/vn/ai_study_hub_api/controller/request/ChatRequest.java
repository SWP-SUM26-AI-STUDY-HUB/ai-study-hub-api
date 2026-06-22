package vn.ai_study_hub_api.controller.request;

import lombok.Data;

import java.util.UUID;

@Data
public class ChatRequest {

    /** Specific document to query, or null to query all of the user's documents. */
    private UUID documentId;

    private String query;

    /** Existing session to continue, or null to create a new session. */
    private UUID sessionId;
}
