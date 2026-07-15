package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.model.NotificationEntity;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.model.UserStatus;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.ReportRepository;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.TagRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.repository.ReviewRepository;
import vn.ai_study_hub_api.service.UploadProvider;
import org.springframework.data.domain.Page;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UploadProvider uploadProvider;

    @Mock
    private DocumentRagClient ragClient;

    @Mock
    private DocumentPreviewGenerator previewGenerator;
    @Mock
    private StoragePlanRepository storagePlanRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ModerationStreamProducer moderationStreamProducer;

    // Spied real instance: @InjectMocks injects it into the constructor AND real
    // mapping logic runs (so query tests still assert actual projection behavior).
    @Spy
    private DocumentMapper documentMapper = new DocumentMapper();

    @InjectMocks
    private DocumentServiceImpl documentService;

    private UserEntity mockUser;
    private TagEntity mockTag;
    private DocumentEntity mockDocument;
    private UUID userId;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        documentId = UUID.randomUUID();

        // Inject the Value annotation values since MockitoExtension won't inject them
        org.springframework.test.util.ReflectionTestUtils.setField(documentService, "maxFileSizeBytes", 52428800L);

        mockUser = UserEntity.builder()
                .id(userId)
                .email("testuser@example.com")
                .fullName("Test User")
                .status(UserStatus.ACTIVE)
                .build();

        mockTag = TagEntity.builder()
                .id(1)
                .label("Study")
                .build();

        mockDocument = DocumentEntity.builder()
                .id(documentId)
                .uploader(mockUser)
                .title("test.pdf")
                .fileUrl("documents/mock-key.pdf")
                .fileType("pdf")
                .fileSizeBytes(100L)
                .status(DocumentStatus.UPLOADING)
                .visibility(DocumentVisibility.PRIVATE)
                .tags(Collections.singletonList(mockTag))
                .build();
    }

    @Test
    void initiateUpload_Success() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        StoragePlanEntity mockPlan = StoragePlanEntity.builder()
                .id(1)
                .name("Free")
                .storageLimit(1L * 1024L * 1024L * 1024L) // 1 GB in bytes
                .maxAiRequestsPerDay(15)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(mockPlan));
        when(tagRepository.findById(1)).thenReturn(Optional.of(mockTag));
        when(uploadProvider.generateStoragePath(any(UUID.class), any(UUID.class), anyString())).thenReturn("mock-user-id/mock-uuid.pdf");
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> {
            DocumentEntity savedDoc = invocation.getArgument(0);
            // Verify that the document id was pre-populated with a non-null UUID
            assertNotNull(savedDoc.getId());
            return savedDoc;
        });

        DocumentEntity result = documentService.initiateUpload(file, "My Custom Title", List.of(1), "Doc Description", DocumentVisibility.PUBLIC, userId);

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("My Custom Title", result.getTitle());
        assertEquals("Doc Description", result.getDescription());
        assertEquals(DocumentVisibility.PUBLIC, result.getVisibility());
        assertEquals(DocumentStatus.UPLOADING, result.getStatus());
        assertEquals(mockUser, result.getUploader());
        assertEquals("mock-user-id/mock-uuid.pdf", result.getFileUrl());
        assertEquals(1, result.getTags().size());
        assertEquals("Study", result.getTags().get(0).getLabel());

        verify(userRepository, times(1)).findById(userId);
        verify(tagRepository, times(1)).findById(1);
        verify(uploadProvider, times(1)).generateStoragePath(eq(userId), any(UUID.class), eq("test.pdf"));
        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
    }

    @Test
    void initiateUpload_UserNotFound() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> 
                documentService.initiateUpload(file, null, List.of(1), null, null, userId)
        );

        verify(documentRepository, never()).save(any(DocumentEntity.class));
    }

    @Test
    void processDocumentAsync_Success_Private() {
        File tempFile = mock(File.class);
        when(tempFile.exists()).thenReturn(true);
        when(tempFile.delete()).thenReturn(true);

        String storagePath = userId.toString() + "/mock-uuid.pdf";
        String contentType = "application/pdf";

        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(mockDocument));
        when(uploadProvider.generatePresignedUrl(storagePath)).thenReturn("https://presigned.url/test.pdf");


        documentService.processDocumentAsync(documentId, tempFile, storagePath, contentType);

        verify(uploadProvider, times(1)).upload(tempFile, storagePath, contentType);
        verify(uploadProvider, times(1)).generatePresignedUrl(storagePath);
        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
        verify(userRepository, times(1)).save(mockUser);
        verify(ragClient, times(1)).triggerProcess(eq(documentId), anyString());
        assertEquals(100L, mockUser.getStorageUsed());
        assertEquals(DocumentStatus.PROCESSING, mockDocument.getStatus());
        verify(tempFile, times(1)).delete();
    }

    @Test
    void processDocumentAsync_Success_Public() {
        mockDocument.setVisibility(DocumentVisibility.PUBLIC);

        File tempFile = mock(File.class);
        when(tempFile.exists()).thenReturn(true);
        when(tempFile.delete()).thenReturn(true);

        String storagePath = userId.toString() + "/mock-uuid.pdf";
        String contentType = "application/pdf";

        UserEntity adminUser = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .fullName("Admin User")
                .role(UserRole.ADMIN)
                .build();

        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(mockDocument));
        when(userRepository.findAllByRole(UserRole.ADMIN)).thenReturn(List.of(adminUser));

        documentService.processDocumentAsync(documentId, tempFile, storagePath, contentType);

        verify(uploadProvider, times(1)).upload(tempFile, storagePath, contentType);
        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
        verify(userRepository, times(1)).save(mockUser);
        assertEquals(100L, mockUser.getStorageUsed());
        assertEquals(DocumentStatus.PENDING, mockDocument.getStatus());
        
        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
        verify(tempFile, times(1)).delete();
    }

    @Test
    void processDocumentAsync_UploadFailure() {
        File tempFile = mock(File.class);
        when(tempFile.exists()).thenReturn(true);
        when(tempFile.delete()).thenReturn(true);

        String storagePath = userId.toString() + "/mock-uuid.pdf";
        String contentType = "application/pdf";

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        doThrow(new RuntimeException("Upload failed")).when(uploadProvider).upload(any(), any(), any());

        documentService.processDocumentAsync(documentId, tempFile, storagePath, contentType);

        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
        assertEquals(DocumentStatus.FAILED, mockDocument.getStatus());
        verify(tempFile, times(1)).delete();
    }

    @Test
    void handleFastApiCallback_Success_Private() {
        mockDocument.setStatus(DocumentStatus.PROCESSING);
        mockDocument.setVisibility(DocumentVisibility.PRIVATE);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(mockDocument);

        documentService.handleFastApiCallback(documentId, "SUCCESS", "LLM Markdown Summary");

        assertEquals(DocumentStatus.COMPLETED, mockDocument.getStatus());
        assertEquals("LLM Markdown Summary", mockDocument.getSummary());

        verify(documentRepository, times(1)).findById(documentId);
        verify(documentRepository, times(1)).save(mockDocument);
    }

    @Test
    void handleFastApiCallback_Success_Public() {
        mockDocument.setStatus(DocumentStatus.PROCESSING);
        mockDocument.setVisibility(DocumentVisibility.PUBLIC);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(mockDocument);

        documentService.handleFastApiCallback(documentId, "SUCCESS", "LLM Markdown Summary");

        assertEquals(DocumentStatus.COMPLETED, mockDocument.getStatus());
        assertEquals("LLM Markdown Summary", mockDocument.getSummary());

        verify(documentRepository, times(1)).findById(documentId);
        verify(documentRepository, times(1)).save(mockDocument);
    }

    @Test
    void handleFastApiCallback_Failed() {
        mockDocument.setStatus(DocumentStatus.PROCESSING);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(mockDocument);

        documentService.handleFastApiCallback(documentId, "FAILED", null);

        assertEquals(DocumentStatus.FAILED, mockDocument.getStatus());
        assertNull(mockDocument.getSummary());

        verify(documentRepository, times(1)).findById(documentId);
        verify(documentRepository, times(1)).save(mockDocument);
    }

    @Test
    void getPersonalDocuments_Success() {
        mockDocument.setDescription("Test description");
        when(documentRepository.findActiveDocumentsByUploaderId(userId)).thenReturn(List.of(mockDocument));

        List<vn.ai_study_hub_api.controller.response.DocumentResponse> result = documentService.getPersonalDocuments(userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(mockDocument.getId(), result.get(0).getId());
        assertEquals(mockDocument.getTitle(), result.get(0).getTitle());
        assertEquals("pdf", result.get(0).getFileType());
        assertEquals("Test description", result.get(0).getDescription());
        assertNotNull(result.get(0).getTags());
        assertEquals(1, result.get(0).getTags().size());
        assertEquals("Study", result.get(0).getTags().get(1));

        verify(documentRepository, times(1)).findActiveDocumentsByUploaderId(userId);
    }

    @Test
    void getPersonalDocuments_OverLimitStorage_AllowedToList() {
        when(documentRepository.findActiveDocumentsByUploaderId(userId)).thenReturn(List.of(mockDocument));

        List<vn.ai_study_hub_api.controller.response.DocumentResponse> result = documentService.getPersonalDocuments(userId);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getTrashDocuments_Success() {
        mockDocument.setDeletedAt(java.time.LocalDateTime.now());
        mockDocument.setStatus(vn.ai_study_hub_api.model.DocumentStatus.DELETED);
        when(documentRepository.findSoftDeletedDocumentsByUploaderId(userId)).thenReturn(List.of(mockDocument));

        List<vn.ai_study_hub_api.controller.response.DocumentResponse> result = documentService.getTrashDocuments(userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(mockDocument.getId(), result.get(0).getId());
        assertEquals("DELETED", result.get(0).getStatus());
        assertNotNull(result.get(0).getDeletedAt());

        verify(documentRepository, times(1)).findSoftDeletedDocumentsByUploaderId(userId);
    }

    @Test
    void deleteDocument_OverLimitStorage_RestoresToActive_WhenUnderLimit() {
        mockUser.setStatus(UserStatus.OVERLIMITSTORAGE);
        mockUser.setStorageUsed(200L);
        mockUser.setPlanId(1);
        mockDocument.setStatus(DocumentStatus.COMPLETED);
        mockDocument.setFileSizeBytes(150L);

        StoragePlanEntity freePlan = StoragePlanEntity.builder()
                .id(1)
                .storageLimit(100L)
                .build();

        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(mockDocument));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(freePlan));

        documentService.deleteDocument(documentId, userId);

        assertEquals(50L, mockUser.getStorageUsed());
        assertEquals(UserStatus.ACTIVE, mockUser.getStatus());
        verify(userRepository, times(1)).save(mockUser);
    }

    @Test
    void deleteDocument_OverLimitStorage_StaysOverLimit_WhenStillExceedsLimit() {
        mockUser.setStatus(UserStatus.OVERLIMITSTORAGE);
        mockUser.setStorageUsed(200L);
        mockUser.setPlanId(1);
        mockDocument.setStatus(DocumentStatus.COMPLETED);
        mockDocument.setFileSizeBytes(10L);

        StoragePlanEntity freePlan = StoragePlanEntity.builder()
                .id(1)
                .storageLimit(100L)
                .build();

        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(mockDocument));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(freePlan));

        documentService.deleteDocument(documentId, userId);

        assertEquals(190L, mockUser.getStorageUsed());
        assertEquals(UserStatus.OVERLIMITSTORAGE, mockUser.getStatus());
    }

    @Test
    void hardDeleteDocument_deletesS3FilesAndDependentsAndRow() {
        String filePath = "userId/" + documentId + ".pdf";
        String previewPath = "userId/" + documentId + "_preview.pdf";
        mockDocument.setFileUrl(filePath);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(previewGenerator.getPreviewStoragePath(filePath)).thenReturn(previewPath);

        documentService.hardDeleteDocument(documentId);

        verify(uploadProvider, times(1)).delete(filePath);
        verify(uploadProvider, times(1)).delete(previewPath);
        verify(reviewRepository, times(1)).deleteByDocumentId(documentId);
        verify(reportRepository, times(1)).deleteByDocumentId(documentId);
        verify(documentRepository, times(1)).deleteSessionDocumentsByDocumentId(documentId);
        verify(documentRepository, times(1)).delete(mockDocument);
    }

    @Test
    void initiateUpload_UserOverlimitStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        mockUser.setStatus(UserStatus.OVERLIMITSTORAGE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        AppException exception = assertThrows(AppException.class, () -> 
                documentService.initiateUpload(file, "My Custom Title", List.of(1), "Doc Description", DocumentVisibility.PUBLIC, userId)
        );
        assertEquals("Your storage has exceeded the plan limit. Please delete files or upgrade your plan to upload", exception.getMessage());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void initiateUpload_UnsupportedFileFormat() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "image content".getBytes()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        AppException exception = assertThrows(AppException.class, () -> 
                documentService.initiateUpload(file, "My Custom Title", List.of(1), "Doc Description", DocumentVisibility.PUBLIC, userId)
        );
        assertEquals("Unsupported file format", exception.getMessage());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void initiateUpload_StorageQuotaExceeded() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        mockUser.setStorageUsed(1073741824L); // 1 GB limit reached

        StoragePlanEntity mockPlan = StoragePlanEntity.builder()
                .id(1)
                .name("Free")
                .storageLimit(1L * 1024L * 1024L * 1024L) // 1 GB in bytes
                .maxAiRequestsPerDay(15)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(mockPlan));

        AppException exception = assertThrows(AppException.class, () -> 
                documentService.initiateUpload(file, "My Custom Title", List.of(1), "Doc Description", DocumentVisibility.PUBLIC, userId)
        );
        assertEquals("Upload failed: file size exceeds remaining storage quota", exception.getMessage());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void initiateUpload_FileSizeExceeded() {
        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("big.pdf");
        when(file.getSize()).thenReturn(52428801L); // 50MB + 1 byte

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.initiateUpload(file, "Big Doc", List.of(1), null, DocumentVisibility.PRIVATE, userId)
        );
        assertEquals("Uploaded file size exceeds the 50MB limit. Please choose another file", exception.getMessage());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void generateShareLink_Success() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentEntity result = documentService.generateShareLink(documentId, userId);

        assertNotNull(result);
        assertNotNull(result.getLinkShare());
        assertTrue(result.getLinkShare().startsWith("doc-"));
        verify(documentRepository, times(1)).findById(documentId);
        verify(documentRepository, times(1)).save(mockDocument);
    }

    @Test
    void generateShareLink_DocumentNotFound() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                documentService.generateShareLink(documentId, userId)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Document not found", exception.getMessage());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void generateShareLink_NotOwner() {
        UUID otherUserId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.generateShareLink(documentId, otherUserId)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("You are not the owner of this document", exception.getMessage());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void getSharedDocument_Success() {
        String token = "doc-123456";
        mockDocument.setLinkShare(token);
        when(documentRepository.findByLinkShare(token)).thenReturn(Optional.of(mockDocument));

        DocumentEntity result = documentService.getSharedDocument(token);

        assertNotNull(result);
        assertEquals(mockDocument, result);
        verify(documentRepository, times(1)).findByLinkShare(token);
    }

    @Test
    void getSharedDocument_NotFound() {
        String token = "doc-123456";
        when(documentRepository.findByLinkShare(token)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                documentService.getSharedDocument(token)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Shared document not found", exception.getMessage());
    }

    @Test
    void getSharedDocument_Deleted() {
        String token = "doc-123456";
        mockDocument.setDeletedAt(java.time.LocalDateTime.now());
        when(documentRepository.findByLinkShare(token)).thenReturn(Optional.of(mockDocument));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.getSharedDocument(token)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Shared document not found", exception.getMessage());
    }

    @Test
    void initiateUpload_Failure_TagNotFound() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        StoragePlanEntity mockPlan = StoragePlanEntity.builder()
                .id(1)
                .name("Free")
                .storageLimit(1L * 1024L * 1024L * 1024L) // 1 GB in bytes
                .maxAiRequestsPerDay(15)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(mockPlan));
        when(tagRepository.findById(999)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
                documentService.initiateUpload(file, "My Custom Title", List.of(999), "Doc Description", DocumentVisibility.PUBLIC, userId)
        );

        assertEquals("Tag not found with ID: 999", exception.getMessage());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void getPreviewAccess_PublicCompleted_GuestSuccess() {
        UUID docId = UUID.randomUUID();
        LocalDateTime testCreatedAt = LocalDateTime.of(2026, 6, 12, 10, 0);
        TagEntity tag = TagEntity.builder().id(1).label("Math").build();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Public Doc")
                .fileUrl("owner-id/doc-id.pdf")
                .fileType("pdf")
                .fileSizeBytes(1024L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PUBLIC)
                .createdAt(testCreatedAt)
                .description("Math test document")
                .tags(Collections.singletonList(tag))
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(uploadProvider.generatePresignedUrl("owner-id/doc-id_preview.pdf"))
                .thenReturn("https://presigned-url/public_preview.pdf");
        when(reviewRepository.calculateAverageRating(docId)).thenReturn(4.5);
        when(reviewRepository.countByDocumentId(docId)).thenReturn(12L);

        vn.ai_study_hub_api.controller.response.DocumentAccessResponse response = documentService.getPreviewAccess(docId, null);

        assertNotNull(response);
        assertEquals(docId, response.getDocumentId());
        assertEquals("Public Doc", response.getTitle());
        assertEquals("https://presigned-url/public_preview.pdf", response.getPresignedUrl());
        assertEquals(testCreatedAt, response.getCreatedAt());
        assertEquals("Math test document", response.getDescription());
        assertEquals("Test User", response.getUploaderName());
        assertEquals(4.5, response.getRating());
        assertEquals(12L, response.getReviewCount());
        assertEquals(0, response.getDownloadCount());
        assertEquals(1, response.getTags().size());
        assertEquals("Math", response.getTags().get(0));

        verify(documentRepository, times(1)).findById(docId);
        verify(uploadProvider, times(1)).generatePresignedUrl("owner-id/doc-id_preview.pdf");
        verify(reviewRepository, times(1)).calculateAverageRating(docId);
        verify(reviewRepository, times(1)).countByDocumentId(docId);
    }

    @Test
    void getPreviewAccess_PublicCompleted_AuthenticatedSuccess() {
        UUID docId = UUID.randomUUID();
        LocalDateTime testCreatedAt = LocalDateTime.of(2026, 6, 12, 10, 0);
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Public Doc")
                .fileUrl("owner-id/doc-id.pdf")
                .fileType("pdf")
                .fileSizeBytes(1024L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PUBLIC)
                .createdAt(testCreatedAt)
                .description("Math test document")
                .build();

        vn.ai_study_hub_api.security.CustomUserDetails otherUserDetails = new vn.ai_study_hub_api.security.CustomUserDetails(
                UUID.randomUUID(),
                "other@example.com",
                "hashed-password",
                true,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(uploadProvider.generatePresignedUrl(doc.getFileUrl()))
                .thenReturn("https://presigned-url/public.pdf");

        vn.ai_study_hub_api.controller.response.DocumentAccessResponse response = documentService.getPreviewAccess(docId, otherUserDetails);

        assertNotNull(response);
        assertEquals(docId, response.getDocumentId());
        assertEquals("https://presigned-url/public.pdf", response.getPresignedUrl());
        assertEquals(testCreatedAt, response.getCreatedAt());
        assertEquals("Math test document", response.getDescription());
        assertEquals(0, response.getDownloadCount());
    }

    @Test
    void getPreviewAccess_Private_GuestUnauthorized() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Private Doc")
                .fileUrl("owner-id/doc-id-private.pdf")
                .fileType("pdf")
                .fileSizeBytes(2048L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.getPreviewAccess(docId, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(uploadProvider, never()).generatePresignedUrl(anyString());
    }

    @Test
    void getPreviewAccess_Private_OwnerSuccess() {
        UUID docId = UUID.randomUUID();
        LocalDateTime testCreatedAt = LocalDateTime.of(2026, 6, 12, 10, 0);
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Private Doc")
                .fileUrl("owner-id/doc-id-private.pdf")
                .fileType("pdf")
                .fileSizeBytes(2048L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .createdAt(testCreatedAt)
                .description("Private description")
                .build();

        vn.ai_study_hub_api.security.CustomUserDetails ownerDetails = new vn.ai_study_hub_api.security.CustomUserDetails(
                userId,
                "owner@example.com",
                "hashed-password",
                true,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(uploadProvider.generatePresignedUrl(doc.getFileUrl()))
                .thenReturn("https://presigned-url/private.pdf");

        vn.ai_study_hub_api.controller.response.DocumentAccessResponse response = documentService.getPreviewAccess(docId, ownerDetails);

        assertNotNull(response);
        assertEquals(docId, response.getDocumentId());
        assertEquals("https://presigned-url/private.pdf", response.getPresignedUrl());
        assertEquals(testCreatedAt, response.getCreatedAt());
        assertEquals("Private description", response.getDescription());
    }

    @Test
    void getPreviewAccess_Private_AdminSuccess() {
        UUID docId = UUID.randomUUID();
        LocalDateTime testCreatedAt = LocalDateTime.of(2026, 6, 12, 10, 0);
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Private Doc")
                .fileUrl("owner-id/doc-id-private.pdf")
                .fileType("pdf")
                .fileSizeBytes(2048L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .createdAt(testCreatedAt)
                .description("Private description")
                .build();

        vn.ai_study_hub_api.security.CustomUserDetails adminDetails = new vn.ai_study_hub_api.security.CustomUserDetails(
                UUID.randomUUID(),
                "admin@example.com",
                "hashed-password",
                true,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(uploadProvider.generatePresignedUrl(doc.getFileUrl()))
                .thenReturn("https://presigned-url/private-admin.pdf");

        vn.ai_study_hub_api.controller.response.DocumentAccessResponse response = documentService.getPreviewAccess(docId, adminDetails);

        assertNotNull(response);
        assertEquals("https://presigned-url/private-admin.pdf", response.getPresignedUrl());
        assertEquals(testCreatedAt, response.getCreatedAt());
        assertEquals("Private description", response.getDescription());
    }

    @Test
    void getPreviewAccess_Private_NonOwnerForbidden() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Private Doc")
                .fileUrl("owner-id/doc-id-private.pdf")
                .fileType("pdf")
                .fileSizeBytes(2048L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .build();

        vn.ai_study_hub_api.security.CustomUserDetails otherUserDetails = new vn.ai_study_hub_api.security.CustomUserDetails(
                UUID.randomUUID(),
                "other@example.com",
                "hashed-password",
                true,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.getPreviewAccess(docId, otherUserDetails)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(uploadProvider, never()).generatePresignedUrl(anyString());
    }

    @Test
    void getPreviewAccess_DocumentNotFound() {
        UUID docId = UUID.randomUUID();
        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        vn.ai_study_hub_api.security.CustomUserDetails ownerDetails = new vn.ai_study_hub_api.security.CustomUserDetails(
                userId,
                "owner@example.com",
                "hashed-password",
                true,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );

        AppException exception = assertThrows(AppException.class, () ->
                documentService.getPreviewAccess(docId, ownerDetails)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void getPreviewAccess_DocumentDeleted() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Private Doc")
                .fileUrl("owner-id/doc-id-private.pdf")
                .fileType("pdf")
                .fileSizeBytes(2048L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .deletedAt(java.time.LocalDateTime.now())
                .build();

        vn.ai_study_hub_api.security.CustomUserDetails ownerDetails = new vn.ai_study_hub_api.security.CustomUserDetails(
                userId,
                "owner@example.com",
                "hashed-password",
                true,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.getPreviewAccess(docId, ownerDetails)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void getDocumentById_NotFound_Throws404() {
        UUID docId = UUID.randomUUID();
        when(documentRepository.findByIdWithUploader(docId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                documentService.getDocumentById(docId, null)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void getDocumentById_Deleted_Throws404() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .status(DocumentStatus.DELETED)
                .visibility(DocumentVisibility.PUBLIC)
                .deletedAt(java.time.LocalDateTime.now())
                .build();

        when(documentRepository.findByIdWithUploader(docId)).thenReturn(Optional.of(doc));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.getDocumentById(docId, null)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void getDocumentById_PublicCompleted_GuestSuccess() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Public Doc")
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PUBLIC)
                .build();

        when(documentRepository.findByIdWithUploader(docId)).thenReturn(Optional.of(doc));

        vn.ai_study_hub_api.controller.response.DocumentResponse response =
                documentService.getDocumentById(docId, null);

        assertNotNull(response);
        assertEquals(docId, response.getId());
        assertEquals("Public Doc", response.getTitle());
        assertEquals("PUBLIC", response.getVisibility());
    }

    @Test
    void getDocumentById_Private_OwnerSuccess() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Private Doc")
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .build();

        vn.ai_study_hub_api.security.CustomUserDetails ownerDetails = new vn.ai_study_hub_api.security.CustomUserDetails(
                userId,
                "owner@example.com",
                "hashed-password",
                true,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );

        when(documentRepository.findByIdWithUploader(docId)).thenReturn(Optional.of(doc));

        vn.ai_study_hub_api.controller.response.DocumentResponse response =
                documentService.getDocumentById(docId, ownerDetails);

        assertNotNull(response);
        assertEquals(docId, response.getId());
        assertEquals("Private Doc", response.getTitle());
        assertEquals("PRIVATE", response.getVisibility());
    }

    @Test
    void getDocumentById_Private_NonOwnerForbidden() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Private Doc")
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .build();

        vn.ai_study_hub_api.security.CustomUserDetails otherUserDetails = new vn.ai_study_hub_api.security.CustomUserDetails(
                UUID.randomUUID(),
                "other@example.com",
                "hashed-password",
                true,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );

        when(documentRepository.findByIdWithUploader(docId)).thenReturn(Optional.of(doc));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.getDocumentById(docId, otherUserDetails)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void getDocumentById_Private_GuestUnauthorized() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .build();

        when(documentRepository.findByIdWithUploader(docId)).thenReturn(Optional.of(doc));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.getDocumentById(docId, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void getDownloadAccess_GuestUnauthorized() {
        UUID docId = UUID.randomUUID();
        AppException exception = assertThrows(AppException.class, () ->
                documentService.getDownloadAccess(docId, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(documentRepository, never()).findById(any());
    }

    @Test
    void getDownloadAccess_PublicCompleted_Success() {
        UUID docId = UUID.randomUUID();
        LocalDateTime testCreatedAt = LocalDateTime.of(2026, 6, 12, 10, 0);
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Public Doc")
                .fileUrl("owner-id/doc-id.pdf")
                .fileType("pdf")
                .fileSizeBytes(1024L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PUBLIC)
                .createdAt(testCreatedAt)
                .description("Math test document")
                .build();

        vn.ai_study_hub_api.security.CustomUserDetails otherUserDetails = new vn.ai_study_hub_api.security.CustomUserDetails(
                UUID.randomUUID(),
                "other@example.com",
                "hashed-password",
                true,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(uploadProvider.generatePresignedUrl(doc.getFileUrl()))
                .thenReturn("https://presigned-url/download.pdf");

        vn.ai_study_hub_api.controller.response.DocumentAccessResponse response = documentService.getDownloadAccess(docId, otherUserDetails);

        assertNotNull(response);
        assertEquals("https://presigned-url/download.pdf", response.getPresignedUrl());
        assertEquals(testCreatedAt, response.getCreatedAt());
        assertEquals("Math test document", response.getDescription());
        assertEquals(1, response.getDownloadCount());
        assertEquals(1, doc.getDownloadCount());
        verify(documentRepository, times(1)).save(doc);
    }

    @Test
    void getDownloadAccess_Private_OwnerSuccess() {
        UUID docId = UUID.randomUUID();
        LocalDateTime testCreatedAt = LocalDateTime.of(2026, 6, 12, 10, 0);
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Private Doc")
                .fileUrl("owner-id/doc-id-private.pdf")
                .fileType("pdf")
                .fileSizeBytes(2048L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .createdAt(testCreatedAt)
                .description("Private description")
                .build();

        vn.ai_study_hub_api.security.CustomUserDetails ownerDetails = new vn.ai_study_hub_api.security.CustomUserDetails(
                userId,
                "owner@example.com",
                "hashed-password",
                true,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(uploadProvider.generatePresignedUrl(doc.getFileUrl()))
                .thenReturn("https://presigned-url/download-owner.pdf");

        vn.ai_study_hub_api.controller.response.DocumentAccessResponse response = documentService.getDownloadAccess(docId, ownerDetails);

        assertNotNull(response);
        assertEquals("https://presigned-url/download-owner.pdf", response.getPresignedUrl());
        assertEquals(testCreatedAt, response.getCreatedAt());
        assertEquals("Private description", response.getDescription());
        assertEquals(1, response.getDownloadCount());
        assertEquals(1, doc.getDownloadCount());
        verify(documentRepository, times(1)).save(doc);
    }

    @Test
    void getDownloadAccess_Private_AdminSuccess() {
        UUID docId = UUID.randomUUID();
        LocalDateTime testCreatedAt = LocalDateTime.of(2026, 6, 12, 10, 0);
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Private Doc")
                .fileUrl("owner-id/doc-id-private.pdf")
                .fileType("pdf")
                .fileSizeBytes(2048L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .createdAt(testCreatedAt)
                .description("Private description")
                .build();

        vn.ai_study_hub_api.security.CustomUserDetails adminDetails = new vn.ai_study_hub_api.security.CustomUserDetails(
                UUID.randomUUID(),
                "admin@example.com",
                "hashed-password",
                true,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(uploadProvider.generatePresignedUrl(doc.getFileUrl()))
                .thenReturn("https://presigned-url/download-admin.pdf");

        vn.ai_study_hub_api.controller.response.DocumentAccessResponse response = documentService.getDownloadAccess(docId, adminDetails);

        assertNotNull(response);
        assertEquals("https://presigned-url/download-admin.pdf", response.getPresignedUrl());
        assertEquals(testCreatedAt, response.getCreatedAt());
        assertEquals("Private description", response.getDescription());
        assertEquals(1, response.getDownloadCount());
        assertEquals(1, doc.getDownloadCount());
        verify(documentRepository, times(1)).save(doc);
    }

    @Test
    void getDownloadAccess_Private_NonOwnerForbidden() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Private Doc")
                .fileUrl("owner-id/doc-id-private.pdf")
                .fileType("pdf")
                .fileSizeBytes(2048L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .build();

        vn.ai_study_hub_api.security.CustomUserDetails otherUserDetails = new vn.ai_study_hub_api.security.CustomUserDetails(
                UUID.randomUUID(),
                "other@example.com",
                "hashed-password",
                true,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.getDownloadAccess(docId, otherUserDetails)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(uploadProvider, never()).generatePresignedUrl(anyString());
    }

    @Test
    void searchPublicDocuments_Success() {
        String keyword = "test";
        mockDocument.setStatus(DocumentStatus.COMPLETED);
        mockDocument.setVisibility(DocumentVisibility.PUBLIC);
        when(documentRepository.searchPublicDocuments(keyword, DocumentVisibility.PUBLIC, DocumentStatus.COMPLETED))
                .thenReturn(List.of(mockDocument));

        List<vn.ai_study_hub_api.controller.response.DocumentResponse> result = documentService.searchPublicDocuments(keyword);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(mockDocument.getId(), result.get(0).getId());

        verify(documentRepository, times(1)).searchPublicDocuments(keyword, DocumentVisibility.PUBLIC, DocumentStatus.COMPLETED);
    }

    @Test
    void searchPublicDocuments_NoResults() {
        String keyword = "notfound";
        when(documentRepository.searchPublicDocuments(keyword, DocumentVisibility.PUBLIC, DocumentStatus.COMPLETED))
                .thenReturn(List.of());

        List<vn.ai_study_hub_api.controller.response.DocumentResponse> result =
                documentService.searchPublicDocuments(keyword);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(documentRepository, times(1)).searchPublicDocuments(keyword, DocumentVisibility.PUBLIC, DocumentStatus.COMPLETED);
    }

    @Test
    void searchPublicDocuments_EmptyKeyword() {
        List<vn.ai_study_hub_api.controller.response.DocumentResponse> result = documentService.searchPublicDocuments("   ");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(documentRepository, never()).searchPublicDocuments(anyString(), any(), any());
    }

    @Test
    void getPendingPublicDocuments_Success() {
        mockDocument.setStatus(DocumentStatus.PENDING);
        mockDocument.setVisibility(DocumentVisibility.PUBLIC);
        when(documentRepository.findPendingPublicDocuments(DocumentStatus.PENDING, DocumentVisibility.PUBLIC))
                .thenReturn(List.of(mockDocument));

        List<vn.ai_study_hub_api.controller.response.DocumentResponse> result = documentService.getPendingPublicDocuments();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(mockDocument.getId(), result.get(0).getId());
        assertEquals("PENDING", result.get(0).getStatus());
        assertEquals("PUBLIC", result.get(0).getVisibility());

        verify(documentRepository, times(1)).findPendingPublicDocuments(DocumentStatus.PENDING, DocumentVisibility.PUBLIC);
    }

    @Test
    void approveDocument_Success() {
        mockDocument.setStatus(DocumentStatus.PENDING);
        mockDocument.setVisibility(DocumentVisibility.PUBLIC);
        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(mockDocument));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));


        documentService.approveDocument(documentId);

        assertEquals(DocumentStatus.PROCESSING, mockDocument.getStatus());
        verify(documentRepository, times(1)).save(mockDocument);
        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
    }

    @Test
    void approveDocument_NotFound() {
        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                documentService.approveDocument(documentId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void approveDocument_NotPending() {
        mockDocument.setStatus(DocumentStatus.COMPLETED);
        mockDocument.setVisibility(DocumentVisibility.PUBLIC);
        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(mockDocument));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.approveDocument(documentId)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void rejectDocument_Success() {
        mockDocument.setStatus(DocumentStatus.PENDING);
        mockDocument.setVisibility(DocumentVisibility.PUBLIC);
        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(mockDocument));

        documentService.rejectDocument(documentId, "Contains ads");

        assertEquals(DocumentStatus.REJECTED, mockDocument.getStatus());
        assertEquals("Contains ads", mockDocument.getRejectionReason());
        verify(documentRepository, times(1)).save(mockDocument);
        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
    }

    @Test
    void rejectDocument_NoReason() {
        AppException exception = assertThrows(AppException.class, () ->
                documentService.rejectDocument(documentId, "   ")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void rejectDocument_NotPending() {
        mockDocument.setStatus(DocumentStatus.COMPLETED);
        mockDocument.setVisibility(DocumentVisibility.PUBLIC);
        when(documentRepository.findByIdWithUploader(documentId)).thenReturn(Optional.of(mockDocument));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.rejectDocument(documentId, "Contains ads")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void updateDocument_Success_Private() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Old Title")
                .fileUrl("owner-id/doc.pdf")
                .fileType("pdf")
                .fileSizeBytes(1024L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .tags(new java.util.ArrayList<>())
                .build();

        vn.ai_study_hub_api.controller.request.UpdateDocumentRequest request =
                new vn.ai_study_hub_api.controller.request.UpdateDocumentRequest();
        request.setTitle("New Title");
        request.setDescription("New Description");

        when(documentRepository.findByIdWithUploader(docId)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(doc);

        vn.ai_study_hub_api.controller.response.DocumentResponse response =
                documentService.updateDocument(docId, request, userId);

        assertNotNull(response);
        assertEquals("New Title", response.getTitle());
        assertEquals("New Description", response.getDescription());
        assertEquals("PRIVATE", response.getVisibility());
        verify(documentRepository, times(1)).save(doc);
    }

    @Test
    void updateDocument_Success_PrivateToPublic_TriggersModeration() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Old Title")
                .fileUrl("owner-id/doc.pdf")
                .fileType("pdf")
                .fileSizeBytes(1024L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .tags(new java.util.ArrayList<>())
                .build();

        vn.ai_study_hub_api.controller.request.UpdateDocumentRequest request =
                new vn.ai_study_hub_api.controller.request.UpdateDocumentRequest();
        request.setVisibility("PUBLIC");

        UserEntity admin = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .fullName("Admin User")
                .role(UserRole.ADMIN)
                .build();

        when(documentRepository.findByIdWithUploader(docId)).thenReturn(Optional.of(doc));
        when(userRepository.findAllByRole(UserRole.ADMIN)).thenReturn(List.of(admin));
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(doc);

        vn.ai_study_hub_api.controller.response.DocumentResponse response =
                documentService.updateDocument(docId, request, userId);

        assertNotNull(response);
        assertEquals("PUBLIC", response.getVisibility());
        assertEquals("PENDING", response.getStatus());
        verify(documentRepository, times(1)).save(doc);
        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
        verify(moderationStreamProducer, times(1)).enqueue(docId);
    }

    @Test
    void updateDocument_Forbidden_Public() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Public Doc")
                .fileUrl("owner-id/doc.pdf")
                .fileType("pdf")
                .fileSizeBytes(1024L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PUBLIC)
                .build();

        vn.ai_study_hub_api.controller.request.UpdateDocumentRequest request =
                new vn.ai_study_hub_api.controller.request.UpdateDocumentRequest();
        request.setTitle("New Title");

        when(documentRepository.findByIdWithUploader(docId)).thenReturn(Optional.of(doc));

        AppException exception = assertThrows(AppException.class, () ->
                documentService.updateDocument(docId, request, userId)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Cannot edit title, description, or tags of a public document", exception.getMessage());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void updateDocument_Success_PublicToPrivate() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Public Doc")
                .fileUrl("owner-id/doc.pdf")
                .fileType("pdf")
                .fileSizeBytes(1024L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PUBLIC)
                .build();

        vn.ai_study_hub_api.controller.request.UpdateDocumentRequest request =
                new vn.ai_study_hub_api.controller.request.UpdateDocumentRequest();
        request.setVisibility("PRIVATE");

        when(documentRepository.findByIdWithUploader(docId)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentEntity.class))).thenReturn(doc);

        vn.ai_study_hub_api.controller.response.DocumentResponse response =
                documentService.updateDocument(docId, request, userId);

        assertNotNull(response);
        assertEquals("PRIVATE", response.getVisibility());
        verify(documentRepository, times(1)).save(doc);
    }

    @Test
    void getRecommendedDocuments_UserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                documentService.getRecommendedDocuments(userId, 0, 8)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("User not found.", exception.getMessage());
    }

    @Test
    void getRecommendedDocuments_NoPreferredTags() {
        mockUser.setPreferredTagIds(Collections.emptyList());
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        Page<vn.ai_study_hub_api.controller.response.DocumentResponse> result =
                documentService.getRecommendedDocuments(userId, 0, 8);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getRecommendedDocuments_NoDocsFound() {
        mockUser.setPreferredTagIds(List.of(1, 2));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(documentRepository.findRecommendedDocumentIds(List.of(1, 2))).thenReturn(Collections.emptyList());

        Page<vn.ai_study_hub_api.controller.response.DocumentResponse> result =
                documentService.getRecommendedDocuments(userId, 0, 8);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getRecommendedDocuments_Success_Paging() {
        mockUser.setPreferredTagIds(List.of(1, 2));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // Let's mock 10 recommended document UUIDs
        java.util.List<UUID> docIds = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            docIds.add(UUID.randomUUID());
        }
        when(documentRepository.findRecommendedDocumentIds(List.of(1, 2))).thenReturn(docIds);

        // Page 1 size 8 should ask for the last 2 docIds: index 8 and 9
        java.util.List<UUID> expectedPageDocIds = docIds.subList(8, 10);
        
        DocumentEntity doc8 = DocumentEntity.builder().id(docIds.get(8)).title("Doc 8").status(DocumentStatus.COMPLETED).visibility(DocumentVisibility.PUBLIC).build();
        DocumentEntity doc9 = DocumentEntity.builder().id(docIds.get(9)).title("Doc 9").status(DocumentStatus.COMPLETED).visibility(DocumentVisibility.PUBLIC).build();

        when(documentRepository.findAllById(expectedPageDocIds)).thenReturn(List.of(doc8, doc9));

        Page<vn.ai_study_hub_api.controller.response.DocumentResponse> result =
                documentService.getRecommendedDocuments(userId, 1, 8);

        assertNotNull(result);
        assertEquals(10, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        assertEquals("Doc 8", result.getContent().get(0).getTitle());
        assertEquals("Doc 9", result.getContent().get(1).getTitle());
    }
}

