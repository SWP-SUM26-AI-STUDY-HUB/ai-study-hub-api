package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.model.NotificationEntity;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.NotificationRepository;
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

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(tagRepository.findAllById(List.of(1))).thenReturn(List.of(mockTag));
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
        verify(tagRepository, times(1)).findAllById(List.of(1));
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
}
