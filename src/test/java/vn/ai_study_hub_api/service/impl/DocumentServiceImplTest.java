package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import vn.ai_study_hub_api.exception.AppException;
import reactor.core.publisher.Mono;
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
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.TagRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.UploadProvider;

import java.io.File;
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
    private WebClient webClient;

    @Mock
    private StoragePlanRepository storagePlanRepository;

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
        org.springframework.test.util.ReflectionTestUtils.setField(documentService, "fastApiUrl", "http://localhost:8000/api");

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
                .storageLimit(1L) // 1 GB
                .maxAiRequestsPerDay(15)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(mockPlan));
        when(tagRepository.findByLabel("Study")).thenReturn(Optional.of(mockTag));
        when(uploadProvider.generateStoragePath(any(UUID.class), any(UUID.class), anyString())).thenReturn("mock-user-id/mock-uuid.pdf");
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> {
            DocumentEntity savedDoc = invocation.getArgument(0);
            // Verify that the document id was pre-populated with a non-null UUID
            assertNotNull(savedDoc.getId());
            return savedDoc;
        });

        DocumentEntity result = documentService.initiateUpload(file, "My Custom Title", List.of("Study"), "Doc Description", DocumentVisibility.PUBLIC, userId);

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
        verify(tagRepository, times(1)).findByLabel("Study");
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
                documentService.initiateUpload(file, null, List.of("Study"), null, null, userId)
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

        // Mock WebClient call
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        documentService.processDocumentAsync(documentId, tempFile, storagePath, contentType);

        verify(uploadProvider, times(1)).upload(tempFile, storagePath, contentType);
        verify(uploadProvider, times(1)).generatePresignedUrl(storagePath);
        verify(documentRepository, times(1)).save(any(DocumentEntity.class));
        verify(userRepository, times(1)).save(mockUser);
        verify(webClient, times(1)).post();
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
        mockUser.setStatus(vn.ai_study_hub_api.model.UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(documentRepository.findActiveDocumentsByUploaderId(userId)).thenReturn(List.of(mockDocument));

        List<vn.ai_study_hub_api.controller.response.DocumentResponse> result = documentService.getPersonalDocuments(userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(mockDocument.getId(), result.get(0).getId());
        assertEquals(mockDocument.getTitle(), result.get(0).getTitle());

        verify(userRepository, times(1)).findById(userId);
        verify(documentRepository, times(1)).findActiveDocumentsByUploaderId(userId);
    }

    @Test
    void getPersonalDocuments_UserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        vn.ai_study_hub_api.exception.AppException exception = assertThrows(
                vn.ai_study_hub_api.exception.AppException.class,
                () -> documentService.getPersonalDocuments(userId)
        );

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
        verify(documentRepository, never()).findActiveDocumentsByUploaderId(any(UUID.class));
    }

    @Test
    void getPersonalDocuments_OverLimitStorage() {
        mockUser.setStatus(vn.ai_study_hub_api.model.UserStatus.OVERLIMITSTORAGE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        vn.ai_study_hub_api.exception.AppException exception = assertThrows(
                vn.ai_study_hub_api.exception.AppException.class,
                () -> documentService.getPersonalDocuments(userId)
        );

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("Your storage limit has been exceeded! Access denied.", exception.getMessage());
        verify(documentRepository, never()).findActiveDocumentsByUploaderId(any(UUID.class));
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

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
                documentService.initiateUpload(file, "My Custom Title", List.of("Study"), "Doc Description", DocumentVisibility.PUBLIC, userId)
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

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
                documentService.initiateUpload(file, "My Custom Title", List.of("Study"), "Doc Description", DocumentVisibility.PUBLIC, userId)
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
                .storageLimit(1L) // 1 GB
                .maxAiRequestsPerDay(15)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(mockPlan));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
                documentService.initiateUpload(file, "My Custom Title", List.of("Study"), "Doc Description", DocumentVisibility.PUBLIC, userId)
        );
        assertEquals("Upload failed: file size exceeds remaining storage quota", exception.getMessage());
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
    void initiateUpload_Success_WithNewAndExistingTags() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        StoragePlanEntity mockPlan = StoragePlanEntity.builder()
                .id(1)
                .name("Free")
                .storageLimit(1L)
                .maxAiRequestsPerDay(15)
                .build();

        TagEntity mockNewTag = TagEntity.builder()
                .id(2)
                .label("NewTag")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(mockPlan));
        when(tagRepository.findByLabel("Study")).thenReturn(Optional.of(mockTag));
        when(tagRepository.findByLabel("NewTag")).thenReturn(Optional.empty());
        when(tagRepository.save(any(TagEntity.class))).thenReturn(mockNewTag);
        when(uploadProvider.generateStoragePath(any(UUID.class), any(UUID.class), anyString())).thenReturn("mock-user-id/mock-uuid.pdf");
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentEntity result = documentService.initiateUpload(file, "My Custom Title", List.of("Study", "NewTag"), "Doc Description", DocumentVisibility.PUBLIC, userId);

        assertNotNull(result);
        assertEquals(2, result.getTags().size());
        assertEquals("Study", result.getTags().get(0).getLabel());
        assertEquals("NewTag", result.getTags().get(1).getLabel());
    }

    @Test
    void initiateUpload_Failure_TagTooLong() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        StoragePlanEntity mockPlan = StoragePlanEntity.builder()
                .id(1)
                .name("Free")
                .storageLimit(1L)
                .maxAiRequestsPerDay(15)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(storagePlanRepository.findById(1)).thenReturn(Optional.of(mockPlan));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
                documentService.initiateUpload(file, "My Custom Title", List.of("this-tag-is-extremely-long-and-exceeds-thirty-characters"), "Doc Description", DocumentVisibility.PUBLIC, userId)
        );
        assertEquals("Tag length cannot exceed 30 characters", exception.getMessage());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void getPreviewAccess_PublicCompleted_GuestSuccess() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Public Doc")
                .fileUrl("owner-id/doc-id.pdf")
                .fileType("pdf")
                .fileSizeBytes(1024L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PUBLIC)
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(uploadProvider.generatePresignedUrl(doc.getFileUrl()))
                .thenReturn("https://presigned-url/public.pdf");

        vn.ai_study_hub_api.controller.response.DocumentAccessResponse response = documentService.getPreviewAccess(docId, null);

        assertNotNull(response);
        assertEquals(docId, response.getDocumentId());
        assertEquals("Public Doc", response.getTitle());
        assertEquals("https://presigned-url/public.pdf", response.getPresignedUrl());
        verify(documentRepository, times(1)).findById(docId);
        verify(uploadProvider, times(1)).generatePresignedUrl(doc.getFileUrl());
    }

    @Test
    void getPreviewAccess_PublicCompleted_AuthenticatedSuccess() {
        UUID docId = UUID.randomUUID();
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Public Doc")
                .fileUrl("owner-id/doc-id.pdf")
                .fileType("pdf")
                .fileSizeBytes(1024L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PUBLIC)
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
    }

    @Test
    void getPreviewAccess_Private_AdminSuccess() {
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
        DocumentEntity doc = DocumentEntity.builder()
                .id(docId)
                .uploader(mockUser)
                .title("Public Doc")
                .fileUrl("owner-id/doc-id.pdf")
                .fileType("pdf")
                .fileSizeBytes(1024L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PUBLIC)
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
    }

    @Test
    void getDownloadAccess_Private_OwnerSuccess() {
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
    }

    @Test
    void getDownloadAccess_Private_AdminSuccess() {
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
}

