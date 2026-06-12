package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.request.TagDocumentRequest;
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


    @PutMapping("/{documentId}/tags")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Tag a document",
            description = "Allows the document owner to associate tags with the document. Reuses existing tags or creates new ones if they don't exist. Maximum 5 tags."
    )
    public ApiResponse<DocumentDetailResponse> tagDocument(
            @PathVariable UUID documentId,
            @RequestBody TagDocumentRequest request) {

        UUID userId = getCurrentUserId();
        DocumentDetailResponse response = documentEditService.tagDocument(documentId, userId, request.getTags());

        return ApiResponse.success(response, "Document tags updated successfully.");
    }


    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }
}
