package vn.ai_study_hub_api.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.service.DocumentService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal Documents", description = "Internal callback endpoints for external service communication")
public class InternalDocumentController {

    private final DocumentService documentService;

    @Value("${app.internal.secret}")
    private String internalSecret;

    @PostMapping("/callback")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "FastAPI processing callback", description = "Callback endpoint called by FastAPI to return LLM summary and finalize status (public/private/failed).")
    public ApiResponse<Void> handleFastApiCallback(
            @RequestHeader(value = "X-Internal-Secret", required = false) String providedSecret,
            @RequestBody CallbackRequest request) {
        
        if (providedSecret == null || !providedSecret.equals(internalSecret)) {
            log.error("Unauthorized callback attempt: invalid internal secret");
            throw new AppException(HttpStatus.FORBIDDEN, "Access Denied: Invalid internal secret");
        }

        log.info("Received callback for document ID: {}, status: {}", request.getDocumentId(), request.getStatus());

        try {
            documentService.handleFastApiCallback(
                    request.getDocumentId(),
                    request.getStatus(),
                    request.getSummary()
            );
            return ApiResponse.success("Callback processed successfully");
        } catch (IllegalArgumentException e) {
            log.error("Failed to process callback: {}", e.getMessage());
            throw new AppException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Internal error processing callback", e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    @Getter
    @Setter
    public static class CallbackRequest {
        @JsonProperty("document_id")
        private UUID documentId;

        private String status;

        private String summary;
    }
}
