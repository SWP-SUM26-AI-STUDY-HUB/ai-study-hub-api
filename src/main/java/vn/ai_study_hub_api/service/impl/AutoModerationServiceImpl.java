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
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.repository.DocumentChunkRepository;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.projection.ChunkContentProjection;
import vn.ai_study_hub_api.service.AutoModerationService;
import vn.ai_study_hub_api.service.DocumentService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
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
    private final DocumentImageExtractor imageExtractor;
    private final LangfuseIngestionClient langfuseIngestion;

    @org.springframework.beans.factory.annotation.Autowired
    @Lazy
    private DocumentService documentService;

    @Value("${openai.api-key}")
    private String openAiApiKey;

    @Value("${openai.moderation-url:https://api.openai.com/v1/moderations}")
    private String moderationUrl;

    @Value("${app.moderation.image.enabled:true}")
    private boolean imageModerationEnabled;

    @Value("${app.moderation.image.max-per-doc:30}")
    private int imageMaxPerDoc;

    @Value("${app.moderation.image.batch-size:5}")
    private int imageBatchSize;
    @Value("${app.moderation.langfuse.enabled:true}")
    private boolean langfuseEnabled;

    /** Nominal per-image token estimate (the Moderation API returns no usage object). */
    private static final int IMAGE_TOKENS_ESTIMATE = 85;
    /** Rough text-token heuristic (chars/token); Vietnamese skews higher — used for volume only. */
    private static final int TEXT_CHARS_PER_TOKEN = 4;
    /** Default model name when the Moderation response omits it (Langfuse cost lookup key). */
    private static final String MODERATION_MODEL = "omni-moderation-latest";

    @Override
    public void process(UUID documentId) {
        log.info("Starting moderation for document ID: {}", documentId);

        // 1. Fetch document
        DocumentEntity document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.warn("Moderation aborted: document {} not found", documentId);
            return;
        }

        // Only moderate PENDING public documents. This is the idempotency guard: a redelivered
        // message for a doc already approved/rejected/completed is a no-op (safe under at-least-once).
        if (!DocumentStatus.PENDING.equals(document.getStatus())) {
            log.info("Moderation skipped: document {} status is {} (not PENDING)", documentId, document.getStatus());
            return;
        }

        // 2. Fetch text chunks
        List<ChunkContentProjection> chunks = chunkRepository.findChunkContentsByDocumentId(documentId);
        if (chunks == null || chunks.isEmpty()) {
            log.warn("Moderation: no chunks for document {}. Leaving PENDING for manual review.", documentId);
            return;
        }

        List<String> inputs = chunks.stream()
                .map(ChunkContentProjection::getContent)
                .filter(content -> content != null && !content.trim().isEmpty())
                .collect(Collectors.toList());
        if (inputs.isEmpty()) {
            log.warn("Moderation: all chunks empty for document {}. Leaving PENDING.", documentId);
            return;
        }

        // OpenAI key not configured (empty / mock) -> skip, leave PENDING.
        if (openAiApiKey == null || openAiApiKey.trim().isEmpty() || "mock_key".equalsIgnoreCase(openAiApiKey)) {
            log.warn("OpenAI API key not set / mock. Skipping moderation; document {} remains PENDING.", documentId);
            return;
        }

        // 3. Text moderation via OpenAI Moderation API in batches (max 30 chunks per request).
        //    No catch here — failures propagate to the stream listener, which leaves the message
        //    unacked so it is redelivered (retry / DLQ), instead of silently swallowing the error.
        ScoreTracker tracker = new ScoreTracker();
        // One Langfuse trace per document; one generation per OpenAI batch. Accumulated here and
        // flushed after triage. Fail-open: any Langfuse error is swallowed — moderation (the OpenAI
        // calls below) is the source of truth and is never affected.
        String traceId = UUID.randomUUID().toString();
        List<Map<String, Object>> langfuseGenerations = new ArrayList<>();
        final int TEXT_BATCH = 30;
        for (int i = 0; i < inputs.size(); i += TEXT_BATCH) {
            int end = Math.min(inputs.size(), i + TEXT_BATCH);
            List<Object> batch = new ArrayList<>(inputs.subList(i, end));
            log.debug("Sending text batch {} to OpenAI Moderation API for document {}", (i / TEXT_BATCH) + 1, documentId);
            tracker.merge(callModerationTraced(ModerationRequest.builder().input(batch).build(),
                    batch, "text", traceId, langfuseGenerations));
        }

        // 3b. Image moderation: extract embedded raster images (PDF/DOCX) from the original file and
        //     classify them too. Failures here are caught (NOT propagated) — they defer the document to
        //     manual review (PENDING) rather than risk an auto-approve on unchecked images.
        boolean imagesChecked = true;
        if (imageModerationEnabled) {
            try {
                List<DocumentImageExtractor.ExtractedImage> images =
                        imageExtractor.extract(document.getFileUrl(), document.getFileType(), imageMaxPerDoc);
                if (!images.isEmpty()) {
                    int total = images.size();
                    for (int i = 0; i < total; i += imageBatchSize) {
                        int end = Math.min(total, i + imageBatchSize);
                        List<Object> batch = new ArrayList<>(end - i);
                        for (DocumentImageExtractor.ExtractedImage img : images.subList(i, end)) {
                            String dataUrl = "data:" + img.mimeType() + ";base64,"
                                    + Base64.getEncoder().encodeToString(img.data());
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("type", "image_url");
                            item.put("image_url", Map.of("url", dataUrl));
                            batch.add(item);
                        }
                        tracker.merge(callModerationTraced(ModerationRequest.builder().input(batch).build(),
                                batch, "image", traceId, langfuseGenerations));
                    }
                    log.info("Moderated {} image(s) for document {}", total, documentId);
                }
            } catch (Exception e) {
                log.warn("Image moderation failed for document {}; deferring to manual review (PENDING)", documentId, e);
                imagesChecked = false;
            }
        }

        log.info("Moderation completed for document {}. Max violation score: {} ({})",
                documentId, tracker.max, tracker.category);

        // 4. Triage by max category score. A clear violation (>=0.80) still auto-rejects. Auto-approve
        //    (<0.40) additionally requires that image moderation — when enabled — completed cleanly;
        //    otherwise the document is left PENDING for manual admin review.
        String decision;
        if (tracker.max >= 0.80) {
            String rejectReason = String.format("Tài liệu bị từ chối tự động do vi phạm tiêu chuẩn cộng đồng: %s (Mức độ vi phạm: %.2f)",
                    tracker.category, tracker.max);
            log.info("Moderation: document {} auto-rejected. Reason: {}", documentId, rejectReason);
            documentService.rejectDocument(documentId, rejectReason);
            decision = "REJECTED";
        } else if (tracker.max < 0.40 && imagesChecked) {
            log.info("Moderation: document {} auto-approved.", documentId);
            documentService.approveDocument(documentId);
            decision = "APPROVED";
        } else {
            log.info("Moderation: document {} left PENDING for manual review (score: {}, imagesChecked: {}).",
                    documentId, tracker.max, imagesChecked);
            decision = "PENDING_MANUAL_REVIEW";
        }

        // 5. Flush the accumulated Langfuse trace + generations (fail-open; moderation already ran).
        flushLangfuseTrace(traceId, document, decision, tracker.max, tracker.category,
                imagesChecked, inputs.size(), langfuseGenerations);
    }

    /** Sends a moderation request and blocks for the response. Throws on OpenAI errors (propagated by the caller). */
    private ModerationResponse callModeration(ModerationRequest payload) {
        return webClient.post()
                .uri(moderationUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(ModerationResponse.class)
                .block();
    }
    /**
     * Wraps {@link #callModeration} with Langfuse generation capture: times the call, estimates
     * input tokens (the Moderation API returns no usage), and appends a {@code generation-create}
     * event. The underlying exception still propagates — on error an ERROR-level generation is
     * recorded first (so failed batches are visible in Langfuse) then rethrown; the moderation
     * retry contract (unacked message → retry/DLQ) is unchanged.
     */
    private ModerationResponse callModerationTraced(ModerationRequest payload, List<Object> rawInput,
                                                    String batchKind, String traceId,
                                                    List<Map<String, Object>> generationEvents) {
        String generationId = UUID.randomUUID().toString();
        String startIso = Instant.now().toString();
        long startNanos = System.nanoTime();
        try {
            ModerationResponse response = callModeration(payload);
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            String model = (response != null && response.getModel() != null)
                    ? response.getModel() : MODERATION_MODEL;
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("batch_kind", batchKind);
            meta.put("item_count", rawInput == null ? 0 : rawInput.size());
            meta.put("latency_ms", durationMs);
            generationEvents.add(LangfuseIngestionClient.generationEvent(
                    traceId, generationId, "openai-moderation", model,
                    startIso, Instant.now().toString(), "DEFAULT", null,
                    estimateTokens(rawInput), 0, meta));
            return response;
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("batch_kind", batchKind);
            meta.put("item_count", rawInput == null ? 0 : rawInput.size());
            meta.put("latency_ms", durationMs);
            generationEvents.add(LangfuseIngestionClient.generationEvent(
                    traceId, generationId, "openai-moderation", MODERATION_MODEL,
                    startIso, Instant.now().toString(), "ERROR", e.toString(),
                    estimateTokens(rawInput), 0, meta));
            throw e;
        }
    }

    /** Estimates token usage for a moderation batch (the OpenAI endpoint returns no usage). */
    private static int estimateTokens(List<Object> input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        for (Object item : input) {
            if (item instanceof String s) {
                tokens += (int) Math.ceil(s.length() / (double) TEXT_CHARS_PER_TOKEN);
            } else {
                // image input (image_url map) — nominal per-image estimate; no real usage returned
                tokens += IMAGE_TOKENS_ESTIMATE;
            }
        }
        return tokens;
    }

    /**
     * Builds the root {@code trace-create} event (with the triage decision as metadata) and flushes
     * it together with the accumulated generations. Entirely fail-open: the {@code enabled} flag is
     * off, Langfuse keys are blank, or any build/transport error is swallowed — moderation has
     * already completed above, so observability must never perturb it.
     */
    private void flushLangfuseTrace(String traceId, DocumentEntity document, String decision,
                                    double maxScore, String category, boolean imagesChecked,
                                    int textChunkCount, List<Map<String, Object>> generationEvents) {
        if (!langfuseEnabled || !langfuseIngestion.isConfigured()) {
            return;
        }
        try {
            // getUploader() is a lazy proxy; reading its @Id does NOT initialize it (Hibernate keeps
            // the FK in the proxy), so this is safe outside a session. Null-checked regardless.
            String userId = (document.getUploader() != null && document.getUploader().getId() != null)
                    ? document.getUploader().getId().toString() : null;
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("document_id", document.getId() != null ? document.getId().toString() : null);
            meta.put("decision", decision);
            meta.put("max_score", maxScore);
            meta.put("category", category);
            meta.put("images_checked", imagesChecked);
            meta.put("text_chunk_count", textChunkCount);

            List<Map<String, Object>> batch = new ArrayList<>();
            batch.add(LangfuseIngestionClient.traceEvent(
                    traceId, "moderation", userId, List.of("moderation", "backend"), meta));
            batch.addAll(generationEvents);
            langfuseIngestion.ingest(batch);
        } catch (Exception e) {
            log.warn("Langfuse trace flush skipped (fail-open): {}", e.toString());
        }
    }

    /** Running maximum category score + its category across all moderation responses (text + images). */
    private static final class ScoreTracker {
        double max = 0.0;
        String category = "";

        void merge(ModerationResponse response) {
            if (response == null || response.getResults() == null) return;
            for (ModerationResult result : response.getResults()) {
                if (result.getCategoryScores() == null) continue;
                for (Map.Entry<String, Double> entry : result.getCategoryScores().entrySet()) {
                    if (entry.getValue() > max) {
                        max = entry.getValue();
                        category = entry.getKey();
                    }
                }
            }
        }
    }

    // --- DTOs for OpenAI Moderation API ---

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModerationRequest {
        // Each item is a plain String (text) or a Map (image_url object) per the omni-moderation-latest schema.
        private List<Object> input;
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
