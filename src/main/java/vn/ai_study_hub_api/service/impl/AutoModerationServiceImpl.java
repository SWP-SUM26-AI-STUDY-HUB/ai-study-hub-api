package vn.ai_study_hub_api.service.impl;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.repository.DocumentChunkRepository;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.projection.ChunkContentProjection;
import vn.ai_study_hub_api.service.AutoModerationService;
import vn.ai_study_hub_api.service.DocumentService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoModerationServiceImpl implements AutoModerationService {

    private final DocumentChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final WebClient webClient;

    @org.springframework.beans.factory.annotation.Autowired
    @Lazy
    private DocumentService documentService;

    @Value("${openai.api-key}")
    private String openAiApiKey;

    @Value("${openai.moderation-url:https://api.openai.com/v1/moderations}")
    private String moderationUrl;

    @Async("taskExecutor")
    @Override
    public void moderateDocumentAsync(UUID documentId) {
        log.info("Starting auto-moderation for document ID: {}", documentId);

        try {
            // 1. Fetch document
            DocumentEntity document = documentRepository.findById(documentId).orElse(null);
            if (document == null) {
                log.warn("Auto-moderation aborted: Document not found with ID: {}", documentId);
                return;
            }

            // Only moderate PENDING public documents
            if (!DocumentStatus.PENDING.equals(document.getStatus())) {
                log.info("Auto-moderation skipped: Document {} status is not PENDING (current status: {})", 
                        documentId, document.getStatus());
                return;
            }

            // 2. Fetch chunks
            List<ChunkContentProjection> chunks = chunkRepository.findChunkContentsByDocumentId(documentId);
            if (chunks == null || chunks.isEmpty()) {
                log.warn("Auto-moderation: No chunks found for document ID: {}. Leaving as PENDING for manual review.", documentId);
                return;
            }

            log.info("Retrieved {} chunks for document ID: {}", chunks.size(), documentId);

            // Extract chunk text contents
            List<String> inputs = chunks.stream()
                    .map(ChunkContentProjection::getContent)
                    .filter(content -> content != null && !content.trim().isEmpty())
                    .collect(Collectors.toList());

            if (inputs.isEmpty()) {
                log.warn("Auto-moderation: All chunks were empty for document ID: {}. Leaving as PENDING.", documentId);
                return;
            }

            // 3. Call OpenAI Moderation API in batches (max 30 chunks per request to avoid payload limits)
            final int BATCH_SIZE = 30;
            double maxScore = 0.0;
            String topViolationCategory = "";

            if (openAiApiKey == null || openAiApiKey.trim().isEmpty() || "mock_key".equalsIgnoreCase(openAiApiKey)) {
                log.warn("OpenAI API Key is not set or mock. Skipping auto-moderation. Document {} remains PENDING.", documentId);
                return;
            }

            for (int i = 0; i < inputs.size(); i += BATCH_SIZE) {
                int end = Math.min(inputs.size(), i + BATCH_SIZE);
                List<String> batch = inputs.subList(i, end);
                
                ModerationRequest requestPayload = ModerationRequest.builder().input(batch).build();
                log.debug("Sending batch {} to OpenAI Moderation API for document {}", (i / BATCH_SIZE) + 1, documentId);

                ModerationResponse response = webClient.post()
                        .uri(moderationUrl)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestPayload)
                        .retrieve()
                        .bodyToMono(ModerationResponse.class)
                        .block();

                if (response != null && response.getResults() != null) {
                    for (ModerationResult result : response.getResults()) {
                        if (result.getCategoryScores() != null) {
                            for (Map.Entry<String, Double> entry : result.getCategoryScores().entrySet()) {
                                if (entry.getValue() > maxScore) {
                                    maxScore = entry.getValue();
                                    topViolationCategory = entry.getKey();
                                }
                            }
                        }
                    }
                }
            }

            log.info("Auto-moderation completed for document {}. Max violation score: {} ({})", 
                    documentId, maxScore, topViolationCategory);

            // 4. Update status based on triage rules
            if (maxScore >= 0.80) {
                // RED ZONE: Auto-reject
                String rejectReason = String.format("Tài liệu bị từ chối tự động do vi phạm tiêu chuẩn cộng đồng: %s (Mức độ vi phạm: %.2f)", 
                        topViolationCategory, maxScore);
                log.info("Auto-moderation: Document {} auto-rejected. Reason: {}", documentId, rejectReason);
                documentService.rejectDocument(documentId, rejectReason);
            } else if (maxScore < 0.40) {
                // GREEN ZONE: Auto-approve
                log.info("Auto-moderation: Document {} auto-approved.", documentId);
                documentService.approveDocument(documentId);
            } else {
                // YELLOW ZONE: Leave PENDING for admin review (no status change needed, status is already PENDING)
                log.info("Auto-moderation: Document {} in yellow zone (score: {}). Left as PENDING for admin review.", 
                        documentId, maxScore);
            }

        } catch (Exception e) {
            log.error("Failed to perform auto-moderation for document ID: {}. Leaving as PENDING.", documentId, e);
        }
    }

    // --- DTOs for OpenAI Moderation API ---

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModerationRequest {
        private List<String> input;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModerationResponse {
        private String id;
        private String model;
        private List<ModerationResult> results;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModerationResult {
        private boolean flagged;
        private Map<String, Boolean> categories;
        @com.fasterxml.jackson.annotation.JsonProperty("category_scores")
        private Map<String, Double> categoryScores;
    }
}
