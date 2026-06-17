package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.DocumentResponse;
import vn.ai_study_hub_api.service.DocumentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Documents", description = "Endpoints for admin document moderation (approve/reject)")
public class AdminDocumentController {

    private final DocumentService documentService;

    @GetMapping("/pending")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get pending public documents", description = "Retrieves a list of public documents currently pending administrator approval.")
    public ApiResponse<List<DocumentResponse>> getPendingPublicDocuments() {
        log.info("Admin request to retrieve pending public documents");
        List<DocumentResponse> documents = documentService.getPendingPublicDocuments();
        return ApiResponse.success(documents, "Pending public documents retrieved successfully");
    }

    @PostMapping("/{documentId}/approve")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Approve a pending public document", description = "Approves a pending public document, making it visible to the public and triggering async RAG indexing.")
    public ApiResponse<Void> approveDocument(@PathVariable("documentId") UUID documentId) {
        log.info("Admin request to approve document ID: {}", documentId);
        documentService.approveDocument(documentId);
        return ApiResponse.success("Document approved successfully");
    }

    @PostMapping("/{documentId}/reject")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reject a pending public document", description = "Rejects a pending public document with a reason, keeping it private and notifying the owner.")
    public ApiResponse<Void> rejectDocument(
            @PathVariable("documentId") UUID documentId,
            @RequestBody RejectRequest request) {
        log.info("Admin request to reject document ID: {} with reason: {}", documentId, request.getRejectionReason());
        documentService.rejectDocument(documentId, request.getRejectionReason());
        return ApiResponse.success("Document rejected successfully");
    }

    @Getter
    @Setter
    public static class RejectRequest {
        private String rejectionReason;
    }
}
