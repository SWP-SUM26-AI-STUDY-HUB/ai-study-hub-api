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
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.DocumentService;

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
}
