package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.request.UpdateDocumentRequest;
import vn.ai_study_hub_api.controller.response.DocumentDetailResponse;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.DocumentEditService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Endpoints for study document management")
public class DocumentEditController {

    private final DocumentEditService documentEditService;

    @PutMapping("/{documentId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Update document metadata",
            description = "Allows the document owner to update title, description, tags, and visibility. Changing visibility to PUBLIC submits the document for admin approval."
    )
    public ApiResponse<DocumentDetailResponse> updateDocument(
            @PathVariable UUID documentId,
            @RequestBody UpdateDocumentRequest request) {

        UUID userId = getCurrentUserId();
        DocumentDetailResponse response = documentEditService.updateDocument(documentId, userId, request);

        String message = "Your changes have been saved successfully.";
        if ("PUBLIC".equalsIgnoreCase(request.getVisibility())
                && "PENDING".equals(response.getStatus())) {
            message = "Your changes have been saved. The document has been submitted for admin approval.";
        }

        return ApiResponse.success(response, message);
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }
}
