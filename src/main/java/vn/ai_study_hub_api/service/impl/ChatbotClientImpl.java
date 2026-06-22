package vn.ai_study_hub_api.service.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.service.ChatbotClient;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotClientImpl implements ChatbotClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${fastapi.rag-chat-url}")
    private String chatUrl;

    @Value("${fastapi.chat-timeout-seconds}")
    private int timeout;

    @Override
    public ChatbotResponse chat(String query, UUID userId, UUID documentId) {
        ChatbotRequest req = new ChatbotRequest(
                query,
                userId.toString(),
                documentId == null ? null : documentId.toString()
        );

        log.info("Calling chatbot {} for user={} documentId={}", chatUrl, userId, documentId);

        try {
            return webClient.post()
                    .uri(chatUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(ChatbotResponse.class)
                    .timeout(Duration.ofSeconds(timeout))
                    .block();
        } catch (WebClientResponseException e) {
            // Chatbot returned 4xx (e.g. success=false "No documents found belonging to this user")
            String msg = parseMessage(e.getResponseBodyAsString());
            log.warn("Chatbot rejected request (status={}): {}", e.getStatusCode(), msg);
            throw new AppException(HttpStatus.BAD_REQUEST,
                    msg != null ? msg : "Chatbot rejected the request");
        } catch (Exception e) {
            // Connection refused, timeout, Reactor-wrapped TimeoutException, etc.
            log.error("Chatbot call failed: {}", e.getMessage());
            throw new AppException(HttpStatus.BAD_GATEWAY, "Chatbot service unavailable");
        }
    }

    /**
     * Best-effort extraction of the top-level {@code message} field from a chatbot error body.
     * Returns null if the body is missing or not parseable as JSON.
     */
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
            log.debug("Could not parse chatbot error body as JSON: {}", body);
            return null;
        }
    }
}
