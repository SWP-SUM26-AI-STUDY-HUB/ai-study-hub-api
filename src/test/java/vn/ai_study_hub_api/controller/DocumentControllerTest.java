package vn.ai_study_hub_api.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.DocumentSharedPreviewResponse;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.DocumentService;
import vn.ai_study_hub_api.service.UploadProvider;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private UploadProvider uploadProvider;

    @InjectMocks
    private DocumentController documentController;

    private AutoCloseable closeable;
    private SecurityContext originalSecurityContext;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        originalSecurityContext = SecurityContextHolder.getContext();
        ReflectionTestUtils.setField(documentController, "shareUrlPrefix", "http://localhost:8080/shared/");
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.setContext(originalSecurityContext);
        closeable.close();
    }

    @Test
    void getSharedDocumentPreview_GuestUser_ShouldReturnPreviewUrl() {
        // Arrange
        String token = "sample-token-123";
        UUID docId = UUID.randomUUID();
        UserEntity uploader = UserEntity.builder()
                .id(UUID.randomUUID())
                .fullName("John Doe")
                .email("john@example.com")
                .build();

        DocumentEntity document = DocumentEntity.builder()
                .id(docId)
                .title("Shared Math Document")
                .fileUrl("uploads/math.pdf")
                .uploader(uploader)
                .tags(Collections.emptyList())
                .build();

        when(documentService.getSharedDocument(token)).thenReturn(document);
        // Expecting preview S3 path: uploads/math_preview.pdf
        when(uploadProvider.generatePresignedUrl("uploads/math_preview.pdf")).thenReturn("https://s3.amazonaws.com/preview-math.pdf");

        // Clear security context to simulate an unauthenticated (guest) user
        SecurityContextHolder.clearContext();

        // Act
        ApiResponse<DocumentSharedPreviewResponse> response = documentController.getSharedDocumentPreview(token);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Shared document details retrieved successfully", response.getMessage());
        assertEquals("https://s3.amazonaws.com/preview-math.pdf", response.getData().getPreviewUrl());
        verify(documentService).getSharedDocument(token);
        verify(uploadProvider).generatePresignedUrl("uploads/math_preview.pdf");
    }

    @Test
    void getSharedDocumentPreview_AuthenticatedUser_ShouldReturnOriginalUrl() {
        // Arrange
        String token = "sample-token-123";
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserEntity uploader = UserEntity.builder()
                .id(UUID.randomUUID())
                .fullName("John Doe")
                .email("john@example.com")
                .build();

        DocumentEntity document = DocumentEntity.builder()
                .id(docId)
                .title("Shared Math Document")
                .fileUrl("uploads/math.pdf")
                .uploader(uploader)
                .tags(Collections.emptyList())
                .build();

        when(documentService.getSharedDocument(token)).thenReturn(document);
        // Expecting original S3 path: uploads/math.pdf
        when(uploadProvider.generatePresignedUrl("uploads/math.pdf")).thenReturn("https://s3.amazonaws.com/original-math.pdf");

        // Mock authentication context for registered user
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getId()).thenReturn(userId);

        SecurityContextHolder.setContext(securityContext);

        // Act
        ApiResponse<DocumentSharedPreviewResponse> response = documentController.getSharedDocumentPreview(token);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Shared document details retrieved successfully", response.getMessage());
        assertEquals("https://s3.amazonaws.com/original-math.pdf", response.getData().getPreviewUrl());
        verify(documentService).getSharedDocument(token);
        verify(uploadProvider).generatePresignedUrl("uploads/math.pdf");
    }
}
