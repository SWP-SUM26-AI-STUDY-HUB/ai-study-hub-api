package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.request.DocumentUploadRequest;
import vn.ai_study_hub_api.controller.response.DocumentUploadResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.controller.response.DocumentShareResponse;
import vn.ai_study_hub_api.controller.response.DocumentSharedPreviewResponse;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.DocumentService;
import vn.ai_study_hub_api.service.UploadProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Endpoints for study document management")
public class        DocumentController {

    private final DocumentService documentService;
    private final UploadProvider uploadProvider;

    @Value("${app.share-url-prefix}")
    private String shareUrlPrefix;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Upload a new document", description = "Saves document metadata, writes the file to a temporary file on disk, schedules asynchronous processing, and returns an immediate response.")
    public ApiResponse<DocumentUploadResponse> uploadDocument(
            @Parameter(description = "The study document file to upload (PDF, DOCX, TXT, MD)", required = true)
            @RequestParam("file") MultipartFile file,
            @ModelAttribute DocumentUploadRequest request) {
        
        log.info("Received request to upload file: {} with metadata: title='{}', tags={}, visibility={}", 
                file.getOriginalFilename(), request.getTitle(), request.getTags(), request.getVisibility());

        // Get authenticated user ID
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            log.error("Unauthorized upload attempt: user principal not found in SecurityContext");
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getId();

        try {

            DocumentEntity document = documentService.initiateUpload(
                    file, 
                    request.getTitle(), 
                    request.getTags(), 
                    request.getDescription(), 
                    request.getVisibility(), 
                    userId
            );
            UUID documentId = document.getId();
            String storagePath = document.getFileUrl();

            File tempFile = Files.createTempFile("upload-" + documentId, "-" + file.getOriginalFilename()).toFile();
            file.transferTo(tempFile);
            log.debug("Transferred MultipartFile to temporary file: {}", tempFile.getAbsolutePath());


            documentService.processDocumentAsync(documentId, tempFile, storagePath, file.getContentType());


            DocumentUploadResponse response = DocumentUploadResponse.builder()
                    .documentId(documentId.toString())
                    .status("uploading")
                    .build();
            
            return ApiResponse.success(response, "Document upload initiated successfully");

        } catch (IOException e) {
            log.error("Failed to handle multipart file upload", e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process uploaded file");
        } catch (IllegalArgumentException e) {
            log.warn("Invalid upload arguments: {}", e.getMessage());
            throw new AppException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{documentId}/share")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Generate a read-only share link", description = "Generates a unique cryptographic hash/UUID token and returns a public preview URL.")
    public ApiResponse<DocumentShareResponse> generateShareLink(
            @PathVariable("documentId") UUID documentId) {
        
        log.info("Request to generate share link for document ID: {}", documentId);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            log.error("Unauthorized share link generation attempt");
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getId();

        DocumentEntity document = documentService.generateShareLink(documentId, userId);

        DocumentShareResponse response = DocumentShareResponse.builder()
                .token(document.getLinkShare())
                .shareUrl(shareUrlPrefix + document.getLinkShare())
                .build();

        return ApiResponse.success(response, "Share link generated successfully");
    }

    @GetMapping("/shared/{token}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Preview a shared document", description = "Retrieves metadata and S3 presigned preview URL for a shared document using its token.")
    public ApiResponse<DocumentSharedPreviewResponse> getSharedDocumentPreview(
            @PathVariable("token") String token) {
        
        log.info("Request to preview shared document with token: {}", token);

        DocumentEntity document = documentService.getSharedDocument(token);

        UUID currentUserId = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof CustomUserDetails) {
            currentUserId = ((CustomUserDetails) authentication.getPrincipal()).getId();
        }

        String fileUrl = document.getFileUrl();
        if (currentUserId == null) {
            fileUrl = getPreviewStoragePath(fileUrl);
        }

        String previewUrl = uploadProvider.generatePresignedUrl(fileUrl);

        java.util.List<String> tags = java.util.Collections.emptyList();
        if (document.getTags() != null) {
            final UUID finalCurrentUserId = currentUserId;
            boolean isOwner = document.getUploader() != null && document.getUploader().getId().equals(finalCurrentUserId);

            tags = document.getTags().stream()
                    .filter(t -> isOwner
                            || t.getVisibility() == null
                            || vn.ai_study_hub_api.model.TagVisibility.PUBLIC.equals(t.getVisibility()))
                    .map(vn.ai_study_hub_api.model.TagEntity::getLabel)
                    .collect(java.util.stream.Collectors.toList());
        }

        String uploaderName = document.getUploader().getFullName();
        if (uploaderName == null || uploaderName.trim().isEmpty()) {
            uploaderName = document.getUploader().getEmail();
        }

        DocumentSharedPreviewResponse response = DocumentSharedPreviewResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .description(document.getDescription())
                .summary(document.getSummary())
                .fileType(document.getFileType())
                .fileSizeBytes(document.getFileSizeBytes())
                .uploaderName(uploaderName)
                .tags(tags)
                .previewUrl(previewUrl)
                .createdAt(document.getCreatedAt())
                .build();

        return ApiResponse.success(response, "Shared document details retrieved successfully");
    }

    @org.springframework.web.bind.annotation.GetMapping("/personal")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.OK)
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Get personal documents",
            description = "Lấy danh sách tài liệu cá nhân chưa xóa của user hiện tại, chặn nếu vượt hạn mức lưu trữ"
    )
    public vn.ai_study_hub_api.common.ApiResponse<java.util.List<vn.ai_study_hub_api.controller.response.DocumentResponse>> getPersonalDocuments() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            log.error("Unauthorized getPersonalDocuments attempt: user principal not found in SecurityContext");
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        java.util.UUID userId = userDetails.getId();

        java.util.List<vn.ai_study_hub_api.controller.response.DocumentResponse> documents =
                documentService.getPersonalDocuments(userId);

        return vn.ai_study_hub_api.common.ApiResponse.success(documents, "Personal documents retrieved successfully.");
    }

    /**
     * Search public documents by keyword.
     * Accessible by both guests and authenticated users.
     *
     * AC F-DOC-05 Scenario 1:
     * - Queries document titles, tags, and extracted text content (description/summary)
     * - Filters out soft-deleted, private, pending, or rejected documents
     * - Returns only active (COMPLETED) public documents
     * - Target response time: < 1.5 seconds
     */
    @GetMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Search public documents",
            description = "Tìm kiếm tài liệu công khai theo từ khoá trong title, tags, description, summary. " +
                    "Guest và User đều có thể truy cập. Chỉ trả về tài liệu public, active (COMPLETED), chưa bị xóa."
    )
    public ApiResponse<java.util.List<vn.ai_study_hub_api.controller.response.DocumentResponse>> searchPublicDocuments(
            @Parameter(description = "Từ khoá tìm kiếm", required = true)
            @RequestParam("keyword") String keyword) {

        log.info("Received search request with keyword: '{}'", keyword);

        java.util.List<vn.ai_study_hub_api.controller.response.DocumentResponse> results =
                documentService.searchPublicDocuments(keyword);

        return ApiResponse.success(results, "Search completed successfully.");
    }

    @GetMapping("/recommendations")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get recommended documents", description = "Returns a paginated list of public documents matching the user's preferred tags from the onboarding survey. Sorted by tag match count, average rating, and recency.")
    public vn.ai_study_hub_api.controller.response.DocumentPageResponse getRecommendedDocuments(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "8") int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            log.error("Unauthorized recommendations attempt");
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getId();

        org.springframework.data.domain.Page<vn.ai_study_hub_api.controller.response.DocumentResponse> results =
                documentService.getRecommendedDocuments(userId, page, size);

        vn.ai_study_hub_api.controller.response.DocumentPageResponse pageResponse = new vn.ai_study_hub_api.controller.response.DocumentPageResponse();
        pageResponse.setSuccess(true);
        pageResponse.setMessage("Recommended documents retrieved successfully");
        pageResponse.setData(results);
        return pageResponse;
    }

    @PutMapping("/{documentId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Update a document", description = "Update document title, description, tags, and visibility.")
    public ApiResponse<vn.ai_study_hub_api.controller.response.DocumentResponse> updateDocument(
            @PathVariable("documentId") UUID documentId,
            @RequestBody vn.ai_study_hub_api.controller.request.UpdateDocumentRequest request) {

        log.info("Request to update document ID: {}", documentId);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            log.error("Unauthorized update document attempt");
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getId();

        vn.ai_study_hub_api.controller.response.DocumentResponse response = documentService.updateDocument(documentId, request, userId);

        return ApiResponse.success(response, "Document updated successfully");
    }

    @GetMapping("/trash")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get soft-deleted documents", description = "Retrieves a list of soft-deleted documents for the authenticated user.")
    public vn.ai_study_hub_api.common.ApiResponse<java.util.List<vn.ai_study_hub_api.controller.response.DocumentResponse>> getTrashDocuments() {
        log.info("Request to get trash documents");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            log.error("Unauthorized get trash documents attempt");
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getId();

        java.util.List<vn.ai_study_hub_api.controller.response.DocumentResponse> documents =
                documentService.getTrashDocuments(userId);

        return ApiResponse.success(documents, "Trash documents retrieved successfully");
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Soft-delete a document", description = "Soft-deletes a document and updates user storage quota.")
    public ApiResponse<Void> deleteDocument(
            @PathVariable("documentId") UUID documentId) {
        
        log.info("Request to delete document ID: {}", documentId);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            log.error("Unauthorized delete document attempt");
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getId();

        documentService.deleteDocument(documentId, userId);

        return ApiResponse.success("Document deleted successfully");
    }

    @GetMapping("/{documentId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get document details", description = "Retrieves a document's metadata. The owner sees full details (including private tags); other users only see public, completed documents.")
    public ApiResponse<vn.ai_study_hub_api.controller.response.DocumentResponse> getDocument(
            @Parameter(description = "Document UUID", required = true) @PathVariable("documentId") UUID documentId) {

        log.info("Request to get document details ID: {}", documentId);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = null;
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof CustomUserDetails) {
            userDetails = (CustomUserDetails) authentication.getPrincipal();
        }

        vn.ai_study_hub_api.controller.response.DocumentResponse response = documentService.getDocumentById(documentId, userDetails);
        return ApiResponse.success(response, "Document retrieved successfully");
    }

    @GetMapping("/{id}/preview")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get document preview url", description = "Generates a temporary presigned S3 URL for viewing the document. Guests can access it if the document is public and completed.")
    public ApiResponse<vn.ai_study_hub_api.controller.response.DocumentAccessResponse> getPreviewUrl(
            @Parameter(description = "Document UUID", required = true) @PathVariable("id") UUID id) {
        log.info("Received request to preview document ID: {}", id);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = null;
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof CustomUserDetails) {
            userDetails = (CustomUserDetails) authentication.getPrincipal();
        }

        vn.ai_study_hub_api.controller.response.DocumentAccessResponse response = documentService.getPreviewAccess(id, userDetails);
        return ApiResponse.success(response, "Document preview access generated successfully");
    }

    @GetMapping("/{id}/download")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get document download url", description = "Generates a temporary presigned S3 URL for downloading the document. Always requires authentication.")
    public ApiResponse<vn.ai_study_hub_api.controller.response.DocumentAccessResponse> getDownloadUrl(
            @Parameter(description = "Document UUID", required = true) @PathVariable("id") UUID id) {
        log.info("Received request to download document ID: {}", id);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || (authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        vn.ai_study_hub_api.controller.response.DocumentAccessResponse response = documentService.getDownloadAccess(id, userDetails);
        return ApiResponse.success(response, "Document download access generated successfully");
    }

    @PostMapping("/{documentId}/save")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Save a document to user's saved library", description = "Saves a public completed document to the user's personal saved library.")
    public ApiResponse<Void> saveDocument(@PathVariable("documentId") UUID documentId) {
        log.info("Request to save document ID: {}", documentId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        documentService.saveDocument(documentId, userDetails.getId());
        return ApiResponse.success("Document saved successfully");
    }

    @DeleteMapping("/{documentId}/unsave")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Remove a saved document from library", description = "Removes a bookmarked document from the user's library.")
    public ApiResponse<Void> unsaveDocument(@PathVariable("documentId") UUID documentId) {
        log.info("Request to unsave document ID: {}", documentId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        documentService.unsaveDocument(documentId, userDetails.getId());
        return ApiResponse.success("Document unsaved successfully");
    }

    @GetMapping("/saved")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get list of saved documents", description = "Returns a paginated list of documents saved by the user.")
    public vn.ai_study_hub_api.controller.response.DocumentPageResponse getSavedDocuments(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        org.springframework.data.domain.Page<vn.ai_study_hub_api.controller.response.DocumentResponse> response = 
                documentService.getSavedDocuments(userDetails.getId(), page, size);
        vn.ai_study_hub_api.controller.response.DocumentPageResponse pageResponse = new vn.ai_study_hub_api.controller.response.DocumentPageResponse();
        pageResponse.setSuccess(true);
        pageResponse.setMessage("Saved documents retrieved successfully");
        pageResponse.setData(response);
        return pageResponse;
    }

    @GetMapping("/user/{userId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get public documents of a specific author", description = "Returns a paginated list of public, completed documents uploaded by the given user ID. Accessible publicly.")
    public vn.ai_study_hub_api.controller.response.DocumentPageResponse getPublicDocumentsByUser(
            @PathVariable("userId") UUID userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        org.springframework.data.domain.Page<vn.ai_study_hub_api.controller.response.DocumentResponse> response = 
                documentService.getPublicDocumentsByUser(userId, page, size);
        vn.ai_study_hub_api.controller.response.DocumentPageResponse pageResponse = new vn.ai_study_hub_api.controller.response.DocumentPageResponse();
        pageResponse.setSuccess(true);
        pageResponse.setMessage("Author's public documents retrieved successfully");
        pageResponse.setData(response);
        return pageResponse;
    }

    private String getPreviewStoragePath(String storagePath) {
        if (storagePath == null) {
            return null;
        }
        int lastDot = storagePath.lastIndexOf('.');
        if (lastDot == -1) {
            return storagePath + "_preview";
        }
        return storagePath.substring(0, lastDot) + "_preview" + storagePath.substring(lastDot);
    }
}

