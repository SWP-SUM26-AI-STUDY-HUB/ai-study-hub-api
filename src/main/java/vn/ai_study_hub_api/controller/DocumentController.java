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
import vn.ai_study_hub_api.controller.request.DocumentRequest;
import vn.ai_study_hub_api.controller.response.DocumentUploadResponse;
import vn.ai_study_hub_api.controller.response.DocumentResponse;
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
            @ModelAttribute DocumentRequest request) {
        
        log.info("Received request to upload file: {} with metadata: title='{}', tags={}, visibility={}", 
                file.getOriginalFilename(), request.getTitle(), request.getTags(), request.getVisibility());

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

    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Update document metadata", description = "Updates title, description, visibility, and tags of an existing document. If visibility changes from private to public, triggers moderation.")
    public ApiResponse<DocumentResponse> updateDocument(
            @PathVariable("id") UUID id,
            @RequestBody DocumentRequest request) {
        
        log.info("Received request to update document ID: {} with metadata: title='{}', tags={}, visibility={}", 
                id, request.getTitle(), request.getTags(), request.getVisibility());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            log.error("Unauthorized update attempt: user principal not found in SecurityContext");
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getId();

        try {
            DocumentEntity updatedDocument = documentService.updateDocument(
                    id,
                    request.getTitle(),
                    request.getTags(),
                    request.getDescription(),
                    request.getVisibility(),
                    userId
            );

            java.util.List<String> tagLabels = updatedDocument.getTags() != null
                    ? updatedDocument.getTags().stream().map(vn.ai_study_hub_api.model.TagEntity::getLabel).toList()
                    : java.util.Collections.emptyList();

            DocumentResponse response = DocumentResponse.builder()
                    .id(updatedDocument.getId())
                    .title(updatedDocument.getTitle())
                    .description(updatedDocument.getDescription())
                    .fileUrl(updatedDocument.getFileUrl())
                    .fileType(updatedDocument.getFileType())
                    .fileSizeBytes(updatedDocument.getFileSizeBytes())
                    .status(updatedDocument.getStatus().name())
                    .visibility(updatedDocument.getVisibility().name())
                    .tags(tagLabels)
                    .createdAt(updatedDocument.getCreatedAt())
                    .updatedAt(updatedDocument.getUpdatedAt())
                    .build();

            String message = (updatedDocument.getStatus() == vn.ai_study_hub_api.model.DocumentStatus.PENDING)
                    ? "Your changes have been saved. The document has been submitted for admin approval"
                    : "Document updated successfully";

            return ApiResponse.success(response, message);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid update arguments: {}", e.getMessage());
            throw new AppException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
