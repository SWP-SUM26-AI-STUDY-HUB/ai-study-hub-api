package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.request.ChatRequest;
import vn.ai_study_hub_api.controller.request.RenameSessionRequest;
import vn.ai_study_hub_api.controller.response.ChatMessageResponse;
import vn.ai_study_hub_api.controller.response.ChatResponse;
import vn.ai_study_hub_api.controller.response.ChatSessionResponse;
import vn.ai_study_hub_api.controller.response.QuotaResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.ChatService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Chat", description = "RAG chatbot endpoints: chat, summarize, session management and quota")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Chat with documents",
            description = "Asks a question scoped to one document (documentId) or all of the user's documents (null). "
                    + "Enforces the daily AI quota, persists both user and bot messages, and returns the answer with citations.")
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request) {
        UUID userId = currentUserId();
        log.info("Chat request from user {} (documentId={}, sessionId={})", userId,
                request != null ? request.getDocumentId() : null, request != null ? request.getSessionId() : null);
        return ApiResponse.success(chatService.chat(request, userId), "Chat response generated successfully");
    }

    @GetMapping("/sessions")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "List active chat sessions")
    public ApiResponse<List<ChatSessionResponse>> listSessions() {
        UUID userId = currentUserId();
        return ApiResponse.success(chatService.listSessions(userId), "Chat sessions retrieved successfully");
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get a session's message history")
    public ApiResponse<List<ChatMessageResponse>> getMessages(@PathVariable("sessionId") UUID sessionId) {
        UUID userId = currentUserId();
        return ApiResponse.success(chatService.getMessages(sessionId, userId), "Chat history retrieved successfully");
    }

    @PatchMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Rename a chat session")
    public ApiResponse<Void> renameSession(@PathVariable("sessionId") UUID sessionId,
                                           @RequestBody RenameSessionRequest request) {
        UUID userId = currentUserId();
        chatService.renameSession(sessionId, request != null ? request.getTitle() : null, userId);
        return ApiResponse.success("Chat session renamed successfully");
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Delete a chat session (soft delete)")
    public ApiResponse<Void> deleteSession(@PathVariable("sessionId") UUID sessionId) {
        UUID userId = currentUserId();
        chatService.deleteSession(sessionId, userId);
        return ApiResponse.success("Chat session deleted successfully");
    }

    @GetMapping("/quota")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get the current daily AI quota usage")
    public ApiResponse<QuotaResponse> getQuota() {
        UUID userId = currentUserId();
        return ApiResponse.success(chatService.getQuota(userId), "Quota retrieved successfully");
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        return userDetails.getId();
    }
}
