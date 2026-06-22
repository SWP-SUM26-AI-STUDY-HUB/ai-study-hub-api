package vn.ai_study_hub_api.service.impl;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.request.ChatRequest;
import vn.ai_study_hub_api.controller.response.ChatMessageResponse;
import vn.ai_study_hub_api.controller.response.ChatResponse;
import vn.ai_study_hub_api.controller.response.ChatSessionResponse;
import vn.ai_study_hub_api.controller.response.CitationView;
import vn.ai_study_hub_api.controller.response.QuotaResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.ChatMessageEntity;
import vn.ai_study_hub_api.model.ChatSessionEntity;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.model.MessageSender;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.ChatMessageRepository;
import vn.ai_study_hub_api.repository.ChatSessionRepository;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.AiQuotaService;
import vn.ai_study_hub_api.service.ChatbotClient;
import vn.ai_study_hub_api.service.ChatService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private static final int TITLE_MAX_LENGTH = 50;
    private static final int SNIPPET_MAX_LENGTH = 150;
    private static final Pattern PAGE_PATTERN = Pattern.compile("(?i)page[:\\s]+(\\d+)");

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AiQuotaService aiQuotaService;
    private final ChatbotClient chatbotClient;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // Chat (F-AI-01 / F-AI-02)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public ChatResponse chat(ChatRequest req, UUID userId) {
        if (req == null || req.getQuery() == null || req.getQuery().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Query must not be empty");
        }
        String query = req.getQuery().trim();

        // 1. AI Guard: enforce daily quota BEFORE calling the chatbot.
        AiQuotaService.QuotaInfo quota = aiQuotaService.checkAndIncrement(userId);

        // 2. Validate document access (if a specific document was selected).
        DocumentEntity document = null;
        if (req.getDocumentId() != null) {
            document = loadAccessibleDocument(req.getDocumentId(), userId);
        }

        // 3. Resolve or create the chat session.
        ChatSessionEntity session = resolveSession(req.getSessionId(), userId, query);
        if (document != null && !session.getDocuments().contains(document)) {
            session.getDocuments().add(document);
        }
        session = chatSessionRepository.save(session);

        // 4. Persist the user message.
        ChatMessageEntity userMessage = ChatMessageEntity.builder()
                .id(UUID.randomUUID())
                .session(session)
                .sender(MessageSender.USER)
                .content(query)
                .build();
        chatMessageRepository.save(userMessage);

        // 5. Call the chatbot (blocking JSON).
        ChatbotClient.ChatbotResponse botResponse = chatbotClient.chat(query, userId, req.getDocumentId());
        if (botResponse == null || botResponse.getData() == null) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "Chatbot returned an empty response");
        }
        if (!botResponse.isSuccess()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    botResponse.getMessage() != null ? botResponse.getMessage() : "Chatbot rejected the request");
        }

        String answer = botResponse.getData().getLlmResponse();
        List<CitationView> citations = extractCitations(botResponse.getData().getDebug());

        // 6. Persist the bot message (with structured citations as JSONB).
        String citationsJson = writeCitationsJson(citations);
        ChatMessageEntity botMessage = ChatMessageEntity.builder()
                .id(UUID.randomUUID())
                .session(session)
                .sender(MessageSender.BOT)
                .content(answer != null ? answer : "")
                .citations(citationsJson)
                .build();
        chatMessageRepository.save(botMessage);

        return ChatResponse.builder()
                .sessionId(session.getId())
                .sessionTitle(session.getTitle())
                .answer(answer)
                .citations(citations)
                .remainingRequests(quota.remaining())
                .dailyLimit(quota.dailyLimit())
                .build();
    }

    // ------------------------------------------------------------------
    // Session management (F-AI-03)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(UUID userId) {
        return chatSessionRepository.findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId).stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID sessionId, UUID userId) {
        ChatSessionEntity session = requireOwnedSession(sessionId, userId);
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Override
    @Transactional
    public void renameSession(UUID sessionId, String title, UUID userId) {
        if (title == null || title.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Title must not be empty");
        }
        if (title.length() > 255) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Title must not exceed 255 characters");
        }
        ChatSessionEntity session = requireOwnedSession(sessionId, userId);
        session.setTitle(title.trim());
        chatSessionRepository.save(session);
    }

    @Override
    @Transactional
    public void deleteSession(UUID sessionId, UUID userId) {
        ChatSessionEntity session = requireOwnedSession(sessionId, userId);
        session.setDeletedAt(LocalDateTime.now());
        chatSessionRepository.save(session);
    }

    // ------------------------------------------------------------------
    // Quota (US-MON-02)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public QuotaResponse getQuota(UUID userId) {
        AiQuotaService.QuotaInfo info = aiQuotaService.getUsage(userId);
        return QuotaResponse.builder()
                .currentCount(info.currentCount())
                .dailyLimit(info.dailyLimit())
                .remaining(info.remaining())
                .build();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ChatSessionEntity resolveSession(UUID sessionId, UUID userId, String query) {
        if (sessionId != null) {
            return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Chat session not found"));
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found with ID: " + userId));
        String title = query.length() <= TITLE_MAX_LENGTH ? query : query.substring(0, TITLE_MAX_LENGTH);
        return ChatSessionEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title(title)
                .build();
    }

    private ChatSessionEntity requireOwnedSession(UUID sessionId, UUID userId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Chat session not found"));
    }

    /**
     * Mirrors {@link DocumentServiceImpl#getPreviewAccess} access logic: owner OR
     * (public + completed), never deleted.
     */
    private DocumentEntity loadAccessibleDocument(UUID documentId, UUID userId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        boolean isOwner = document.getUploader() != null && document.getUploader().getId().equals(userId);
        boolean isPublicCompleted = DocumentVisibility.PUBLIC.equals(document.getVisibility())
                && DocumentStatus.COMPLETED.equals(document.getStatus());

        if (!(isOwner || isPublicCompleted)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You do not have access to this document");
        }
        return document;
    }

    private List<CitationView> extractCitations(JsonNode debug) {
        if (debug == null || debug.isMissingNode() || debug.isNull()) {
            return List.of();
        }
        JsonNode docs = debug.path("documents");
        if (!docs.isArray() || docs.isEmpty()) {
            return List.of();
        }
        List<CitationView> result = new ArrayList<>();
        for (JsonNode entry : docs) {
            try {
                JsonNode meta = entry.path("metadata");
                UUID docId = parseUuid(textOrNull(meta, "document_id"));
                String fileName = firstNonBlank(textOrNull(meta, "source_file"), textOrNull(meta, "document_title"));
                Integer pageNumber = pageNumber(meta);
                String snippet = buildSnippet(entry.path("content").asText(""));
                result.add(CitationView.builder()
                        .documentId(docId)
                        .fileName(fileName)
                        .pageNumber(pageNumber)
                        .snippet(snippet)
                        .build());
            } catch (Exception e) {
                log.warn("Skipping malformed citation entry: {}", e.getMessage());
            }
        }
        return result;
    }

    private Integer pageNumber(JsonNode metadata) {
        JsonNode pn = metadata.path("page_number");
        if (pn.isInt() || pn.isLong()) {
            return pn.asInt();
        }
        String chunkCitation = textOrNull(metadata, "chunk_citation");
        if (chunkCitation != null) {
            Matcher m = PAGE_PATTERN.matcher(chunkCitation);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private String buildSnippet(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        String body = content;
        // Retrieval prepends an inline citation tag "[...]\n" to the content; drop it for the snippet.
        int newline = content.indexOf('\n');
        if (content.startsWith("[") && newline > 0 && newline < 200) {
            body = content.substring(newline + 1);
        }
        if (body.length() > SNIPPET_MAX_LENGTH) {
            return body.substring(0, SNIPPET_MAX_LENGTH) + "...";
        }
        return body.isBlank() ? null : body;
    }

    private UUID parseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isTextual() ? child.asText() : null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String writeCitationsJson(List<CitationView> citations) {
        try {
            return objectMapper.writeValueAsString(citations == null ? List.of() : citations);
        } catch (Exception e) {
            log.warn("Failed to serialize citations to JSON: {}", e.getMessage());
            return null;
        }
    }

    private List<CitationView> readCitationsJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CitationView>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse citations JSON '{}': {}", json, e.getMessage());
            return List.of();
        }
    }

    private ChatSessionResponse toSessionResponse(ChatSessionEntity session) {
        return ChatSessionResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private ChatMessageResponse toMessageResponse(ChatMessageEntity message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .sender(message.getSender() != null ? message.getSender().name().toLowerCase() : null)
                .content(message.getContent())
                .citations(readCitationsJson(message.getCitations()))
                .createdAt(message.getCreatedAt())
                .build();
    }
}
