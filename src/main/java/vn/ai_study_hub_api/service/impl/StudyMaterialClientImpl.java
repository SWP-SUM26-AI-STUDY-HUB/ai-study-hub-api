package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.ai_study_hub_api.controller.response.FlashcardItemResponse;
import vn.ai_study_hub_api.controller.response.QuizQuestionResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.service.StudyMaterialClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Blocking WebClient impl for quiz / flashcard generation.
 *
 * <p>The RAG service returns snake_case JSON ({@code correct_index}); it is parsed via
 * {@link JsonNode} and mapped into the camelCase response DTOs, so no {@code @JsonProperty}
 * leaks onto the frontend-facing types. A {@code success:false} body or HTTP 4xx/5xx is
 * surfaced as a {@link vn.ai_study_hub_api.exception.AppException}, matching {@link ChatbotClientImpl}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudyMaterialClientImpl implements StudyMaterialClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${fastapi.rag-quiz-url}")
    private String quizUrl;

    @Value("${fastapi.rag-flashcard-url}")
    private String flashcardUrl;

    @Value("${fastapi.study-material-timeout-seconds:60}")
    private int timeout;

    @Override
    public QuizResult generateQuiz(UUID documentId, Integer count, String focus) {
        JsonNode data = call(quizUrl, documentId, count, focus, "quiz");
        return new QuizResult(
                data.path("debug").path("refused").asBoolean(false),
                data.path("debug").path("reason").asText(""),
                parseQuiz(data.path("quiz")));
    }

    @Override
    public FlashcardResult generateFlashcard(UUID documentId, Integer count, String focus) {
        JsonNode data = call(flashcardUrl, documentId, count, focus, "flashcard");
        return new FlashcardResult(
                data.path("debug").path("refused").asBoolean(false),
                data.path("debug").path("reason").asText(""),
                parseFlashcards(data.path("flashcards")));
    }

    /**
     * POSTs the request and returns the {@code data} node. Throws {@link AppException} on any
     * transport / non-2xx / {@code success:false} outcome — the caller decides the user-facing
     * message via the exception's HttpStatus + message.
     */
    private JsonNode call(String url, UUID documentId, Integer count, String focus, String label) {
        StudyMaterialClient.GenerateRequest req = new StudyMaterialClient.GenerateRequest(
                documentId.toString(), count, focus);
        log.info("Calling RAG {} generation {} for documentId={}", label, url, documentId);
        try {
            String body = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeout))
                    .block();
            return parseEnvelope(body, url);
        } catch (WebClientResponseException e) {
            String msg = parseMessage(e.getResponseBodyAsString());
            log.warn("RAG {} rejected request (status={}): {}", label, e.getStatusCode(), msg);
            throw new AppException(HttpStatus.BAD_REQUEST, msg != null ? msg : "RAG service rejected the request");
        } catch (Exception e) {
            log.error("RAG {} call failed: {}", label, e.getMessage());
            throw new AppException(HttpStatus.BAD_GATEWAY, "RAG service unavailable");
        }
    }

    /** Validates the {@code {success, message, data}} envelope and returns {@code data}. */
    private JsonNode parseEnvelope(String body, String url) {
        if (body == null || body.isBlank()) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "RAG service returned an empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            boolean success = root.path("success").asBoolean(false);
            if (!success) {
                String msg = root.path("message").asText("RAG service rejected the request");
                throw new AppException(HttpStatus.BAD_REQUEST, msg);
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "RAG service returned no data");
            }
            return data;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse RAG response body from {}: {}", url, e.getMessage());
            throw new AppException(HttpStatus.BAD_GATEWAY, "RAG service returned a malformed response");
        }
    }

    private List<QuizQuestionResponse> parseQuiz(JsonNode quizArray) {
        if (!quizArray.isArray()) {
            return List.of();
        }
        List<QuizQuestionResponse> out = new ArrayList<>();
        for (JsonNode q : quizArray) {
            out.add(QuizQuestionResponse.builder()
                    .question(q.path("question").asText(""))
                    .options(objectMapper.convertValue(q.path("options"), new TypeReference<List<String>>() {
                    }))
                    .correctIndex(q.path("correct_index").asInt(0))
                    .explanation(q.path("explanation").asText(""))
                    .build());
        }
        return out;
    }

    private List<FlashcardItemResponse> parseFlashcards(JsonNode cardArray) {
        if (!cardArray.isArray()) {
            return List.of();
        }
        List<FlashcardItemResponse> out = new ArrayList<>();
        for (JsonNode c : cardArray) {
            out.add(FlashcardItemResponse.builder()
                    .term(c.path("term").asText(""))
                    .definition(c.path("definition").asText(""))
                    .build());
        }
        return out;
    }

    private String parseMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode msgNode = root.path("message");
            if (msgNode.isTextual()) {
                String text = msgNode.asText();
                return text.isBlank() ? null : text;
            }
            return null;
        } catch (Exception e) {
            log.debug("Could not parse RAG error body as JSON: {}", body);
            return null;
        }
    }
}
