package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.ai_study_hub_api.common.HashUtil;
import vn.ai_study_hub_api.config.CacheConfig;
import vn.ai_study_hub_api.controller.request.UpdateDocumentRequest;
import vn.ai_study_hub_api.controller.response.DocumentAccessResponse;
import vn.ai_study_hub_api.controller.response.DocumentResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.model.NotificationEntity;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.TagVisibility;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.model.UserStatus;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.ReportRepository;
import vn.ai_study_hub_api.repository.ReviewRepository;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.TagRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.repository.SavedDocumentRepository;
import vn.ai_study_hub_api.model.SavedDocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.DocumentService;
import vn.ai_study_hub_api.service.UploadProvider;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core document business orchestrator: upload validation, the
 * upload/extract/index state machine, owner/admin lifecycle mutations
 * (update / delete / share / approve / reject) and read-side queries.
 *
 * <p>Heavy, reusable machinery lives in focused collaborators extracted for
 * single-responsibility:
 * <ul>
 *   <li>{@link DocumentPreviewGenerator} — preview rendering (PDF/DOCX/TXT).</li>
 *   <li>{@link DocumentRagClient} — FastAPI RAG HTTP transport.</li>
 *   <li>{@link DocumentMapper} — entity &rarr; response projection + tag visibility.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final UploadProvider uploadProvider;
    private final StoragePlanRepository storagePlanRepository;
    private final ReviewRepository reviewRepository;
    private final ReportRepository reportRepository;
    private final SavedDocumentRepository savedDocumentRepository;
    private final DocumentPreviewGenerator previewGenerator;
    private final DocumentRagClient ragClient;
    private final DocumentMapper documentMapper;

    private final ModerationStreamProducer moderationStreamProducer;

    @Value("${app.upload.max-file-size-bytes}")
    private long maxFileSizeBytes;

    @Override
    @Transactional
    public DocumentEntity initiateUpload(MultipartFile file, String title, List<Integer> tags, String description, DocumentVisibility visibility, UUID userId) {
        log.info("Initiating upload for file: {}, user: {}, tags: {}, title: {}, visibility: {}", file.getOriginalFilename(), userId, tags, title, visibility);

        UserEntity uploader = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (UserStatus.OVERLIMITSTORAGE.equals(uploader.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Your storage has exceeded the plan limit. Please delete files or upgrade your plan to upload");
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename).toLowerCase();
        List<String> allowedExtensions = List.of("pdf", "docx", "txt", "md");
        if (originalFilename == null || !allowedExtensions.contains(fileExtension)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Unsupported file format");
        }

        if (file.getSize() > maxFileSizeBytes) {
            long limitMb = maxFileSizeBytes / (1024L * 1024L);
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Uploaded file size exceeds the " + limitMb + "MB limit. Please choose another file");
        }

        Integer planId = uploader.getPlanId() != null ? uploader.getPlanId() : 1;
        StoragePlanEntity plan = storagePlanRepository.findById(planId)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Storage plan not found with ID: " + planId));
        long limitInBytes = plan.getStorageLimit();
        if (uploader.getStorageUsed() + file.getSize() > limitInBytes) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Upload failed: file size exceeds remaining storage quota");
        }
        // Duplicate detection (Tier 1): reject exact re-uploads by SHA-256 content hash.
        String contentHash;
        try {
            contentHash = HashUtil.sha256Hex(file.getBytes());
        } catch (java.io.IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read uploaded file content");
        }
        documentRepository.findFirstByContentHashAndDeletedAtIsNull(contentHash)
                .ifPresent(existing -> {
                    log.warn("Rejected duplicate upload: content hash {} already used by document {}",
                            contentHash, existing.getId());
                    throw new AppException(HttpStatus.CONFLICT,
                            "Document with identical content already exists");
                });

        List<TagEntity> tagEntities = resolveTags(tags, userId);

        UUID documentId = UUID.randomUUID();
        String storagePath = uploadProvider.generateStoragePath(userId, documentId, originalFilename);

        String docTitle = (title != null && !title.trim().isEmpty()) ? title : originalFilename;
        if (docTitle == null || docTitle.isEmpty()) {
            docTitle = "untitled";
        }

        DocumentEntity document = DocumentEntity.builder()
                .id(documentId)
                .uploader(uploader)
                .title(docTitle)
                .fileUrl(storagePath)
                .fileType(fileExtension)
                .fileSizeBytes(file.getSize())
                .contentHash(contentHash)
                .status(DocumentStatus.UPLOADING)
                .description(description)
                .visibility(visibility != null ? visibility : DocumentVisibility.PRIVATE)
                .tags(tagEntities)
                .build();

        return documentRepository.save(document);
    }

    @Async("taskExecutor")
    @Override
    public void processDocumentAsync(UUID documentId, File tempFile, String storagePath, String contentType) {
        log.info("Running background processing for document ID: {}, storagePath: {}", documentId, storagePath);
        try {
            uploadProvider.upload(tempFile, storagePath, contentType);
            log.info("Successfully uploaded document {} to storage", documentId);

            previewGenerator.createAndUploadPreviewFile(tempFile, storagePath, contentType);

            DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

            if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
                log.info("Document ID {} has been deleted, skipping storage and FastAPI processing", documentId);
                return;
            }

            UserEntity uploader = document.getUploader();
            if (uploader != null) {
                long newStorageUsed = uploader.getStorageUsed() + document.getFileSizeBytes();
                uploader.setStorageUsed(newStorageUsed);
                userRepository.save(uploader);
                log.info("Updated storage_used for user {} to {} bytes", uploader.getId(), newStorageUsed);
            }

            if (DocumentVisibility.PUBLIC.equals(document.getVisibility())) {
                // Public -> PENDING (await moderation + approval). Chunks are extracted
                // NOW (embedding deferred) so moderation has content to review while
                // the doc sits in PENDING. EXTRACTED callback keeps it PENDING; /index
                // runs after approval.
                document.setStatus(DocumentStatus.PENDING);
                documentRepository.save(document);
                log.info("Document ID {} is public. Status updated to PENDING.", documentId);

                createPendingApprovalNotifications(document);

                String presignedUrl = uploadProvider.generatePresignedUrl(storagePath);
                ragClient.triggerExtract(documentId, presignedUrl);
            } else {
                document.setStatus(DocumentStatus.PROCESSING);
                documentRepository.save(document);
                log.info("Document ID {} is private. Status updated to PROCESSING.", documentId);

                String presignedUrl = uploadProvider.generatePresignedUrl(storagePath);
                log.info("Triggering FastAPI processing for document: {}", documentId);
                ragClient.triggerProcess(documentId, presignedUrl);
                log.info("FastAPI webhook successfully triggered for document ID: {}", documentId);
            }

        } catch (Exception e) {
            log.error("Failed to complete background processing for document ID: {}", documentId, e);
            updateDocumentStatus(documentId, DocumentStatus.FAILED);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                log.debug("Cleaned up temporary file: {}, success: {}", tempFile.getAbsolutePath(), deleted);
            }
        }
    }

    private void createPendingApprovalNotifications(DocumentEntity document) {
        List<UserEntity> admins = userRepository.findAllByRole(UserRole.ADMIN);
        log.info("Creating pending approval notifications for {} admin(s) for document ID: {}", admins.size(), document.getId());

        String title = "New Document Pending Approval";
        String uploaderName = document.getUploader().getFullName();
        if (uploaderName == null || uploaderName.trim().isEmpty()) {
            uploaderName = document.getUploader().getEmail();
        }
        String content = String.format("Document '%s' uploaded by %s is waiting to approve.", document.getTitle(), uploaderName);

        for (UserEntity admin : admins) {
            NotificationEntity notification = NotificationEntity.builder()
                    .user(admin)
                    .title(title)
                    .content(content)
                    .type("DOCUMENT_PENDING")
                    .targetId(document.getId().toString())
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public void handleFastApiCallback(UUID documentId, String status, String summary) {
        log.info("Processing callback from FastAPI. Document ID: {}, status: {}", documentId, status);

        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

        if ("SUCCESS".equalsIgnoreCase(status)) {
            if (summary != null && !summary.trim().isEmpty()) {
                document.setSummary(summary);
            }
            if (DocumentStatus.PROCESSING.equals(document.getStatus())) {
                document.setStatus(DocumentStatus.COMPLETED);
                log.info("RAG SUCCESS. Document {} -> COMPLETED.", documentId);
            } else {
                log.info("RAG SUCCESS. Document {} status remains {} (not PROCESSING).", documentId, document.getStatus());
            }
        } else if ("EXTRACTED".equalsIgnoreCase(status)) {
            // Public doc extracted (chunks available for moderation), NOT yet embedded.
            // Store summary; status stays PENDING, then fire auto-moderation.
            if (summary != null && !summary.trim().isEmpty()) {
                document.setSummary(summary);
            }
            log.info("RAG EXTRACTED. Document {} chunks ready for moderation; status remains {}. Triggering auto-moderation.", documentId, document.getStatus());
            moderationStreamProducer.enqueue(documentId);
        } else {
            // FAILED (or unknown). A PENDING public doc whose extraction failed also goes FAILED.
            if (DocumentStatus.PROCESSING.equals(document.getStatus())
                    || DocumentStatus.PENDING.equals(document.getStatus())) {
                document.setStatus(DocumentStatus.FAILED);
                log.warn("RAG FAILED. Document {} -> FAILED.", documentId);
            } else {
                log.warn("RAG FAILED. Document {} status remains {}.", documentId, document.getStatus());
            }
        }

        documentRepository.save(document);
    }

    private void updateDocumentStatus(UUID documentId, DocumentStatus newStatus) {
        DocumentEntity document = documentRepository.findById(documentId).orElse(null);
        if (document != null) {
            document.setStatus(newStatus);
            documentRepository.save(document);
            log.info("Document ID {} status updated to {}", documentId, newStatus);
        } else {
            log.error("Could not update status to {} because document ID {} was not found", newStatus, documentId);
        }
    }

    @Override
    @Transactional
    public DocumentEntity generateShareLink(UUID documentId, UUID userId) {
        log.info("Generating share link for document ID: {}, user ID: {}", documentId, userId);

        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        // Owner may mint/rotate the token; any user may share an already-public, completed
        // document — same owner-OR-public+completed rule as ChatService / StudyMaterialService.
        boolean isOwner = document.getUploader() != null && document.getUploader().getId().equals(userId);
        boolean isPublicCompleted = DocumentVisibility.PUBLIC.equals(document.getVisibility())
                && DocumentStatus.COMPLETED.equals(document.getStatus());

        if (!(isOwner || isPublicCompleted)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You do not have access to this document");
        }

        // Owner can regenerate (rotate) the token; a non-owner only reuses the existing one,
        // minting on demand when none exists yet (benign — the document is already public).
        if (isOwner || document.getLinkShare() == null || document.getLinkShare().isBlank()) {
            document.setLinkShare("doc-" + UUID.randomUUID());
            return documentRepository.save(document);
        }
        return document;
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentEntity getSharedDocument(String token) {
        log.info("Retrieving shared document for token: {}", token);

        if (token == null || token.trim().isEmpty()) {
            throw new AppException(HttpStatus.NOT_FOUND, "Shared document not found");
        }

        DocumentEntity document = documentRepository.findByLinkShare(token)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shared document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Shared document not found");
        }

        return document;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getPersonalDocuments(UUID userId) {
        return documentRepository.findActiveDocumentsByUploaderId(userId).stream()
                .map(documentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getTrashDocuments(UUID userId) {
        log.info("Fetching trash documents for user ID: {}", userId);
        return documentRepository.findSoftDeletedDocumentsByUploaderId(userId).stream()
                .map(documentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> searchPublicDocuments(String keyword) {
        log.info("Searching public documents with keyword: '{}'", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            log.warn("Search keyword is empty, returning empty result");
            return List.of();
        }

        String trimmedKeyword = keyword.trim();
        List<DocumentEntity> results = documentRepository.searchPublicDocuments(
                trimmedKeyword, DocumentVisibility.PUBLIC, DocumentStatus.COMPLETED);

        log.info("Found {} public documents matching keyword '{}'", results.size(), trimmedKeyword);

        return results.stream()
                .map(documentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DocumentResponse updateDocument(UUID documentId, UpdateDocumentRequest request, UUID userId) {
        log.info("Updating document ID: {}, requested by user ID: {}", documentId, userId);

        DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        if (!document.getUploader().getId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not the owner of this document");
        }

        if (DocumentVisibility.PUBLIC.equals(document.getVisibility())) {
            if (request.getTitle() != null || request.getDescription() != null || request.getTags() != null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Cannot edit title, description, or tags of a public document");
            }
            if (request.getVisibility() == null || !DocumentVisibility.PRIVATE.name().equalsIgnoreCase(request.getVisibility().trim())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Public documents can only be changed to private");
            }
        }

        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            document.setTitle(request.getTitle().trim());
        }

        if (request.getDescription() != null) {
            document.setDescription(request.getDescription().trim());
        }

        if (request.getTags() != null) {
            document.setTags(resolveTags(request.getTags(), userId));
        }

        boolean needsRagProcessing = false;
        boolean triggerModeration = false;

        if (request.getVisibility() != null && !request.getVisibility().trim().isEmpty()) {
            try {
                DocumentVisibility newVisibility = DocumentVisibility.valueOf(request.getVisibility().trim().toUpperCase());
                if (!document.getVisibility().equals(newVisibility)) {
                    document.setVisibility(newVisibility);

                    if (DocumentVisibility.PUBLIC.equals(newVisibility)) {
                        // PRIVATE -> PUBLIC
                        document.setStatus(DocumentStatus.PENDING);
                        createPendingApprovalNotifications(document);
                        triggerModeration = true;
                    } else {
                        // PUBLIC -> PRIVATE
                        if (DocumentStatus.PENDING.equals(document.getStatus()) || DocumentStatus.REJECTED.equals(document.getStatus())) {
                            // Never indexed (was never approved) -> index as private now.
                            document.setStatus(DocumentStatus.PROCESSING);
                            needsRagProcessing = true;
                        }
                        // COMPLETED or PROCESSING stays as-is.
                    }
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid visibility value: " + request.getVisibility());
            }
        }

        documentRepository.save(document);

        if (needsRagProcessing) {
            triggerFastApiAsync(documentId);
        }

        if (triggerModeration) {
            moderationStreamProducer.enqueue(documentId);
        }
        // NOTE: PRIVATE -> PUBLIC intentionally does NOT call RAG here. The doc was
        // already indexed as private (chunks + embeddings exist), so moderation can
        // read chunks immediately. It enters PENDING; RAG visibility flips to public
        // only after approval (approveDocument).

        return documentMapper.toResponse(document);
    }

    @Async("taskExecutor")
    public void updateFastApiVisibilityAsync(UUID documentId, String visibility) {
        log.info("Updating RAG visibility for document ID: {} to {}", documentId, visibility);
        try {
            ragClient.updateVisibility(documentId, visibility);
        } catch (Exception e) {
            log.error("Failed to update visibility in RAG for document ID: {}", documentId, e);
        }
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_TRENDING_DOCUMENTS, allEntries = true)
    @Transactional
    public void deleteDocument(UUID documentId, UUID userId) {
        log.info("Deleting document ID: {}, requested by user ID: {}", documentId, userId);

        DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        if (!document.getUploader().getId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not the owner of this document");
        }

        DocumentStatus originalStatus = document.getStatus();
        document.setStatusBeforeDeletion(originalStatus);
        document.setDeletedByAdmin(false);
        document.setDeletedAt(LocalDateTime.now());
        document.setStatus(DocumentStatus.DELETED);

        if (!DocumentStatus.UPLOADING.equals(originalStatus)) {
            UserEntity uploader = document.getUploader();
            long newStorageUsed = Math.max(0L, uploader.getStorageUsed() - document.getFileSizeBytes());
            uploader.setStorageUsed(newStorageUsed);

            if (UserStatus.OVERLIMITSTORAGE.equals(uploader.getStatus())) {
                Integer planId = uploader.getPlanId() != null ? uploader.getPlanId() : 1;
                long limitInBytes = storagePlanRepository.findById(planId)
                        .map(StoragePlanEntity::getStorageLimit)
                        .orElse(0L);
                if (newStorageUsed <= limitInBytes) {
                    uploader.setStatus(UserStatus.ACTIVE);
                    log.info("User {} storage back under plan limit, restored to ACTIVE", uploader.getId());
                }
            }

            userRepository.save(uploader);
            log.info("Subtracted {} bytes from user {} storage. New storage: {} bytes",
                    document.getFileSizeBytes(), uploader.getId(), newStorageUsed);
        }

        document.setLinkShare(null);
        documentRepository.save(document);

    }

    @Async("taskExecutor")
    public void deleteFastApiVectorsAsync(UUID documentId) {
        log.info("Deleting FastAPI vectors for document ID: {}", documentId);
        try {
            ragClient.deleteVectors(documentId);
        } catch (Exception e) {
            log.error("Failed to delete vectors in FastAPI for document ID: {}", documentId, e);
        }
    }

    @Override
    @Transactional
    public void hardDeleteDocument(UUID documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        // (1) RAG FIRST: needs document_chunks present to read metadata.doc_id -> parent_ids,
        // so it must run before the documents row (and its cascaded chunks) are deleted.
        // Best-effort: on failure the DB cascade still clears chunks; parent_docs_store on the
        // RAG filesystem would be left as a small leak (parent docs zombie).
        try {
            ragClient.deleteVectors(documentId);
        } catch (Exception e) {
            log.warn("RAG purge (best-effort) failed for {} (DB cascade will clear chunks): {}",
                    documentId, e.getMessage());
        }

        String fileUrl = document.getFileUrl();
        String previewPath = previewGenerator.getPreviewStoragePath(fileUrl);

        // S3 first (idempotent DeleteObject): a later DB failure rolls back, and the next
        // run retries the whole document (re-deleting already-gone S3 keys is a no-op).
        if (fileUrl != null) {
            uploadProvider.delete(fileUrl);
        }
        if (previewPath != null) {
            uploadProvider.delete(previewPath);
        }

        // Application-level dependent cleanup: these FKs have no ON DELETE CASCADE,
        // so they must be removed before the document row or the delete violates them.
        reviewRepository.deleteByDocumentId(documentId);
        reportRepository.deleteByDocumentId(documentId);
        documentRepository.deleteSessionDocumentsByDocumentId(documentId);

        // document_tags is cleared by Hibernate (DocumentEntity owns the @ManyToMany);
        // saved_documents + document_chunks cascade at the DB level.
        documentRepository.delete(document);
        log.info("Permanently deleted document {} (S3 files + DB row + dependent rows)", documentId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_TRENDING_DOCUMENTS, allEntries = true)
    public void restoreDocument(UUID documentId, UUID userId) {
        log.info("Restoring document ID: {}, requested by user ID: {}", documentId, userId);

        DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() == null || !DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Document is not deleted");
        }

        if (Boolean.TRUE.equals(document.getDeletedByAdmin())) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "This document was removed by an administrator and cannot be restored");
        }

        if (!document.getUploader().getId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not the owner of this document");
        }

        DocumentStatus pre = document.getStatusBeforeDeletion();
        if (pre == null || DocumentStatus.UPLOADING.equals(pre)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Document cannot be restored");
        }

        // --- restore lifecycle state ---
        document.setStatus(pre);
        document.setDeletedAt(null);
        document.setStatusBeforeDeletion(null);
        document.setDeletedByAdmin(false);

        // --- restore storage (symmetric to the subtraction done in deleteDocument) ---
        UserEntity uploader = document.getUploader();
        long used = uploader.getStorageUsed() + document.getFileSizeBytes();
        uploader.setStorageUsed(used);
        Integer planId = uploader.getPlanId() != null ? uploader.getPlanId() : 1;
        long limit = storagePlanRepository.findById(planId)
                .map(StoragePlanEntity::getStorageLimit)
                .orElse(0L);
        if (used > limit && UserStatus.ACTIVE.equals(uploader.getStatus())) {
            uploader.setStatus(UserStatus.OVERLIMITSTORAGE); // still readable, only blocks new uploads
        }
        userRepository.save(uploader);

        // --- share link: the old token was nulled at delete-time; mint a new one only for PUBLIC + COMPLETED ---
        if (pre == DocumentStatus.COMPLETED && DocumentVisibility.PUBLIC.equals(document.getVisibility())) {
            document.setLinkShare("doc-" + UUID.randomUUID());
        }
        documentRepository.save(document);

        // --- notify owner ---
        notificationRepository.save(NotificationEntity.builder()
                .user(uploader)
                .title("Document Restored")
                .content("Your document '" + document.getTitle() + "' has been restored.")
                .type("DOCUMENT_RESTORED")
                .targetId(document.getId().toString())
                .isRead(false)
                .build());

        // RAG: nothing to do — the index is intact (lazy purge). That is the whole point of Plan A.
        log.info("Document {} restored to status {}", documentId, pre);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentAccessResponse getPreviewAccess(UUID documentId, CustomUserDetails userDetails) {
        log.info("Getting preview access for document ID: {}, user: {}", documentId, userDetails != null ? userDetails.getId() : "Guest");

        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        boolean hasAccess;
        if (DocumentVisibility.PUBLIC.equals(document.getVisibility()) && DocumentStatus.COMPLETED.equals(document.getStatus())) {
            hasAccess = true;
        } else if (userDetails != null) {
            boolean isOwner = document.getUploader() != null && document.getUploader().getId().equals(userDetails.getId());
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            hasAccess = isOwner || isAdmin;
        } else {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }

        if (!hasAccess) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied.");
        }

        String fileKey = document.getFileUrl();
        if (userDetails == null) {
            fileKey = getPreviewStoragePath(fileKey);
        }

        String presignedUrl = uploadProvider.generatePresignedUrl(fileKey);

        String uploaderName = null;
        if (document.getUploader() != null) {
            uploaderName = document.getUploader().getFullName();
            if (uploaderName == null || uploaderName.trim().isEmpty()) {
                uploaderName = document.getUploader().getEmail();
            }
        }

        Double averageRating = reviewRepository.calculateAverageRating(documentId);
        double ratingVal = averageRating != null ? Math.round(averageRating * 10.0) / 10.0 : 0.0;
        long reviewCount = reviewRepository.countByDocumentId(documentId);

        List<String> tagsList = java.util.Collections.emptyList();
        if (document.getTags() != null) {
            UUID currentUserId = userDetails != null ? userDetails.getId() : null;
            boolean isOwner = document.getUploader() != null && document.getUploader().getId().equals(currentUserId);
            tagsList = document.getTags().stream()
                    .filter(t -> isOwner || t.getVisibility() == null || TagVisibility.PUBLIC.equals(t.getVisibility()))
                    .map(TagEntity::getLabel)
                    .collect(Collectors.toList());
        }

        return DocumentAccessResponse.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .fileType(document.getFileType())
                .fileSizeBytes(document.getFileSizeBytes())
                .presignedUrl(presignedUrl)
                .createdAt(document.getCreatedAt())
                .description(document.getDescription())
                .uploaderName(uploaderName)
                .rating(ratingVal)
                .reviewCount(reviewCount)
                .downloadCount(document.getDownloadCount())
                .tags(tagsList)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(UUID documentId, CustomUserDetails userDetails) {
        log.info("Getting document details ID: {}, user: {}", documentId, userDetails != null ? userDetails.getId() : "Guest");

        DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        boolean hasAccess;
        if (DocumentVisibility.PUBLIC.equals(document.getVisibility()) && DocumentStatus.COMPLETED.equals(document.getStatus())) {
            hasAccess = true;
        } else if (userDetails != null) {
            boolean isOwner = document.getUploader() != null && document.getUploader().getId().equals(userDetails.getId());
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            hasAccess = isOwner || isAdmin;
        } else {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }

        if (!hasAccess) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied.");
        }

        return documentMapper.toResponse(document);
    }

    @Override
    @Transactional
    public DocumentAccessResponse getDownloadAccess(UUID documentId, CustomUserDetails userDetails) {
        log.info("Getting download access for document ID: {}, user: {}", documentId, userDetails != null ? userDetails.getId() : "Guest");

        if (userDetails == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }

        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        boolean hasAccess;
        if (DocumentVisibility.PUBLIC.equals(document.getVisibility()) && DocumentStatus.COMPLETED.equals(document.getStatus())) {
            hasAccess = true;
        } else {
            boolean isOwner = document.getUploader() != null && document.getUploader().getId().equals(userDetails.getId());
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            hasAccess = isOwner || isAdmin;
        }

        if (!hasAccess) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied.");
        }

        document.setDownloadCount(document.getDownloadCount() == null ? 1 : document.getDownloadCount() + 1);
        documentRepository.save(document);

        String presignedUrl = uploadProvider.generatePresignedUrl(document.getFileUrl());

        return DocumentAccessResponse.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .fileType(document.getFileType())
                .fileSizeBytes(document.getFileSizeBytes())
                .presignedUrl(presignedUrl)
                .createdAt(document.getCreatedAt())
                .description(document.getDescription())
                .downloadCount(document.getDownloadCount())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getPendingPublicDocuments() {
        log.info("Fetching pending public documents for moderation");
        List<DocumentEntity> pendingDocs = documentRepository.findPendingPublicDocuments(
                DocumentStatus.PENDING, DocumentVisibility.PUBLIC);
        return pendingDocs.stream()
                .map(documentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_TRENDING_DOCUMENTS, allEntries = true)
    @Transactional
    public void approveDocument(UUID documentId) {
        log.info("Approving public document with ID: {}", documentId);
        DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        if (!DocumentStatus.PENDING.equals(document.getStatus()) || !DocumentVisibility.PUBLIC.equals(document.getVisibility())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only pending public documents can be approved");
        }

        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);

        notifyOwner(document, "Document Approved",
                String.format("Your document '%s' has been approved and is now public.", document.getTitle()),
                "DOCUMENT_APPROVED");

        log.info("Document {} approved -> PROCESSING. Flipping RAG visibility to public + indexing.", documentId);
        // Flip RAG chunk metadata to public, then embed pending chunks (/index).
        updateFastApiVisibilityAsync(documentId, DocumentVisibility.PUBLIC.name());
        triggerFastApiAsync(documentId);
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_TRENDING_DOCUMENTS, allEntries = true)
    @Transactional
    public void rejectDocument(UUID documentId, String reason) {
        log.info("Rejecting public document with ID: {}, reason: {}", documentId, reason);
        if (reason == null || reason.trim().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }

        DocumentEntity document = documentRepository.findByIdWithUploader(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        if (!DocumentStatus.PENDING.equals(document.getStatus()) || !DocumentVisibility.PUBLIC.equals(document.getVisibility())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only pending public documents can be rejected");
        }

        document.setStatus(DocumentStatus.REJECTED);
        document.setRejectionReason(reason.trim());
        documentRepository.save(document);

        notifyOwner(document, "Document Rejected",
                String.format("Your document has been rejected. Reason: %s", reason.trim()),
                "DOCUMENT_REJECTED");

        log.info("Document {} rejected -> REJECTED. Notifying owner + purging extracted/indexed chunks from RAG.", documentId);
        deleteFastApiVectorsAsync(documentId);
    }

    @Async("taskExecutor")
    @Override
    public void triggerFastApiAsync(UUID documentId) {
        log.info("Triggering RAG index for approved document ID: {}", documentId);
        try {
            DocumentEntity document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

            if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
                log.info("Document ID {} has been deleted, skipping RAG index", documentId);
                return;
            }

            // /index embeds already-extracted chunks (idempotent: no-op if embedded).
            ragClient.triggerIndex(documentId);
        } catch (Exception e) {
            log.error("Failed to trigger FastAPI for document ID: {}", documentId, e);
            updateDocumentStatus(documentId, DocumentStatus.FAILED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentResponse> getRecommendedDocuments(UUID userId, int page, int size) {
        log.info("Fetching recommended documents for userId: {} with page: {} size: {}", userId, page, size);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));

        List<Integer> preferredTagIds = user.getPreferredTagIds();
        if (preferredTagIds == null || preferredTagIds.isEmpty()) {
            log.info("User {} has no preferred tags, returning empty recommendations", userId);
            return Page.empty(PageRequest.of(page, size));
        }

        List<UUID> docIds = documentRepository.findRecommendedDocumentIds(preferredTagIds);
        if (docIds.isEmpty()) {
            log.info("No recommended documents found for userId: {}", userId);
            return Page.empty(PageRequest.of(page, size));
        }

        int total = docIds.size();
        Pageable pageable = PageRequest.of(page, size);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);

        if (start >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        List<UUID> pageDocIds = docIds.subList(start, end);
        Map<UUID, DocumentEntity> docMap = documentRepository.findAllById(pageDocIds).stream()
                .collect(Collectors.toMap(DocumentEntity::getId, doc -> doc));

        List<DocumentResponse> content = pageDocIds.stream()
                .map(docMap::get)
                .filter(java.util.Objects::nonNull)
                .map(documentMapper::toResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, total);
    }

    // --- helpers ---

    /** Resolves tag ids to entities, enforcing private-tag ownership. */
    private List<TagEntity> resolveTags(List<Integer> tagIds, UUID userId) {
        List<TagEntity> tagEntities = new ArrayList<>();
        if (tagIds == null) {
            return tagEntities;
        }
        for (Integer tagId : tagIds) {
            if (tagId == null) {
                continue;
            }
            TagEntity tagEntity = tagRepository.findById(tagId)
                    .orElseThrow(() -> new IllegalArgumentException("Tag not found with ID: " + tagId));
            if (TagVisibility.PRIVATE.equals(tagEntity.getVisibility())
                    && (tagEntity.getCreatedBy() == null || !tagEntity.getCreatedBy().getId().equals(userId))) {
                throw new AppException(HttpStatus.FORBIDDEN, "You are not authorized to use another user's private tag");
            }
            tagEntities.add(tagEntity);
        }
        return tagEntities;
    }

    /** Inserts {@code _preview} before the extension: {@code u/d.pdf -> u/d_preview.pdf}. */
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

    private DocumentResponse mapToDocumentResponse(DocumentEntity doc) {
        Map<Integer, String> tags = documentMapper.getVisibleTags(doc);

        UUID currentUserId = documentMapper.getCurrentUserId();
        boolean isSaved = false;
        if (currentUserId != null && savedDocumentRepository != null) {
            isSaved = savedDocumentRepository.existsByUserIdAndDocumentId(currentUserId, doc.getId());
        }

        return DocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .fileName(doc.getTitle())
                .fileUrl(doc.getFileUrl())
                .fileSize(doc.getFileSizeBytes())
                .fileType(doc.getFileType())
                .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                .description(doc.getDescription())
                .tags(tags)
                .uploader(documentMapper.toUploaderResponse(doc))
                .visibility(doc.getVisibility() != null ? doc.getVisibility().name() : null)
                .isSaved(isSaved)
                .createdAt(doc.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public void saveDocument(UUID documentId, UUID userId) {
        log.info("Request by user {} to save document {}", userId, documentId);
        
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found with ID: " + documentId));

        if (document.getDeletedAt() != null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot save a deleted document");
        }

        if (!DocumentStatus.COMPLETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only completed documents can be saved");
        }

        if (!DocumentVisibility.PUBLIC.equals(document.getVisibility()) && 
            (document.getUploader() == null || !document.getUploader().getId().equals(userId))) {
            throw new AppException(HttpStatus.FORBIDDEN, "You do not have permission to save this private document");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found with ID: " + userId));

        if (savedDocumentRepository.existsByUserIdAndDocumentId(userId, documentId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Document is already saved");
        }

        SavedDocumentEntity savedDocument = SavedDocumentEntity.builder()
                .user(user)
                .document(document)
                .build();

        savedDocumentRepository.save(savedDocument);
        log.info("Successfully saved document {} for user {}", documentId, userId);
    }

    @Override
    @Transactional
    public void unsaveDocument(UUID documentId, UUID userId) {
        log.info("Request by user {} to unsave document {}", userId, documentId);
        
        if (!savedDocumentRepository.existsByUserIdAndDocumentId(userId, documentId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Saved document relationship not found");
        }

        savedDocumentRepository.deleteByUserIdAndDocumentId(userId, documentId);
        log.info("Successfully unsaved document {} for user {}", documentId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentResponse> getSavedDocuments(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("savedAt").descending());
        Page<SavedDocumentEntity> savedDocsPage = savedDocumentRepository.findByUserId(userId, pageable);
        return savedDocsPage.map(savedDoc -> mapToDocumentResponse(savedDoc.getDocument()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentResponse> getPublicDocumentsByUser(UUID authorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<DocumentEntity> docsPage = documentRepository.findPublicDocumentsByUploaderId(
                authorId, DocumentVisibility.PUBLIC, DocumentStatus.COMPLETED, pageable);
        return docsPage.map(this::mapToDocumentResponse);
    }

    /** Returns the substring after the last dot, or empty when none/no filename. */
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private void notifyOwner(DocumentEntity document, String title, String content, String type) {
        NotificationEntity notification = NotificationEntity.builder()
                .user(document.getUploader())
                .title(title)
                .content(content)
                .type(type)
                .targetId(document.getId().toString())
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }
}

