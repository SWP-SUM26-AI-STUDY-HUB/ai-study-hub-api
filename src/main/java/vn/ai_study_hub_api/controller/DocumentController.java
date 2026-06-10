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
import vn.ai_study_hub_api.controller.response.DocumentAccessResponse;
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
public class DocumentController {

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
            // Step 2: Create a new document in the database with status = 'uploading'
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

            // Copy MultipartFile input stream to a temporary local file 
            File tempFile = Files.createTempFile("upload-" + documentId, "-" + file.getOriginalFilename()).toFile();
            file.transferTo(tempFile);
            log.debug("Transferred MultipartFile to temporary file: {}", tempFile.getAbsolutePath());

            // Trigger Step 6 & 7: Background processing (Upload, Presign, WebClient POST)
            documentService.processDocumentAsync(documentId, tempFile, storagePath, file.getContentType());

            // Step 4: Return HTTP 200 immediately
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

    @GetMapping("/{id}/preview")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get document preview url", description = "Generates a temporary presigned S3 URL for viewing the document. Guests can access it if the document is public and completed.")
    public ApiResponse<DocumentAccessResponse> getPreviewUrl(
            @Parameter(description = "Document UUID", required = true) @PathVariable("id") UUID id) {
        log.info("Received request to preview document ID: {}", id);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = null;
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof CustomUserDetails) {
            userDetails = (CustomUserDetails) authentication.getPrincipal();
        }

        DocumentAccessResponse response = documentService.getPreviewAccess(id, userDetails);
        return ApiResponse.success(response, "Document preview access generated successfully");
    }

    @GetMapping("/{id}/download")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get document download url", description = "Generates a temporary presigned S3 URL for downloading the document. Always requires authentication.")
    public ApiResponse<DocumentAccessResponse> getDownloadUrl(
            @Parameter(description = "Document UUID", required = true) @PathVariable("id") UUID id) {
        log.info("Received request to download document ID: {}", id);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || (authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        DocumentAccessResponse response = documentService.getDownloadAccess(id, userDetails);
        return ApiResponse.success(response, "Document download access generated successfully");
    }
}
