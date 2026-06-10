package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class    DocumentController {

    private final DocumentService documentService;
    private final UploadProvider uploadProvider;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Upload a new document", description = "Saves document metadata, writes the file to a temporary file on disk, schedules asynchronous processing, and returns an immediate response.")
    public ApiResponse<DocumentUploadResponse> uploadDocument(
            @Parameter(description = "The study document file to upload (PDF, DOCX, TXT, MD)", required = true)
            @RequestParam("file") MultipartFile file,
            @ModelAttribute DocumentUploadRequest request) {
        
        log.info("Received request to upload file: {} with metadata: title='{}', tags={}, visibility={}", 
                file.getOriginalFilename(), request.getTitle(), request.getTagIds(), request.getVisibility());

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
                    request.getTagIds(), 
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
                .shareUrl("https://aistudyhub.com/shared/" + document.getLinkShare())
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

        String previewUrl = uploadProvider.generatePresignedUrl(document.getFileUrl());

        java.util.List<String> tags = java.util.Collections.emptyList();
        if (document.getTags() != null) {
            tags = document.getTags().stream()
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
}
