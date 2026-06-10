package vn.ai_study_hub_api.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.DocumentAccessResponse;
import vn.ai_study_hub_api.exception.AppException;
import org.springframework.http.HttpStatus;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.DocumentService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private DocumentController documentController;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        closeable.close();
    }

    @Test
    void getPreviewUrl_AsGuest_Success() {
        UUID docId = UUID.randomUUID();
        DocumentAccessResponse mockResponse = DocumentAccessResponse.builder()
                .documentId(docId)
                .title("Public Doc")
                .fileType("pdf")
                .fileSizeBytes(100L)
                .presignedUrl("https://presigned-preview-url")
                .build();

        // Clear security context to simulate guest access
        SecurityContextHolder.clearContext();

        when(documentService.getPreviewAccess(eq(docId), any())).thenReturn(mockResponse);

        ApiResponse<DocumentAccessResponse> response = documentController.getPreviewUrl(docId);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Public Doc", response.getData().getTitle());
        assertEquals("https://presigned-preview-url", response.getData().getPresignedUrl());

        verify(documentService, times(1)).getPreviewAccess(eq(docId), eq(null));
    }

    @Test
    void getPreviewUrl_AsUser_Success() {
        UUID docId = UUID.randomUUID();
        DocumentAccessResponse mockResponse = DocumentAccessResponse.builder()
                .documentId(docId)
                .title("Private Doc")
                .fileType("pdf")
                .fileSizeBytes(100L)
                .presignedUrl("https://presigned-preview-url")
                .build();

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.setContext(securityContext);

        when(documentService.getPreviewAccess(eq(docId), eq(userDetails))).thenReturn(mockResponse);

        ApiResponse<DocumentAccessResponse> response = documentController.getPreviewUrl(docId);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("https://presigned-preview-url", response.getData().getPresignedUrl());

        verify(documentService, times(1)).getPreviewAccess(eq(docId), eq(userDetails));
    }

    @Test
    void getDownloadUrl_AsGuest_ThrowsUnauthorized() {
        UUID docId = UUID.randomUUID();

        // Guest session (null authentication)
        SecurityContextHolder.clearContext();

        AppException exception = assertThrows(AppException.class, () -> 
                documentController.getDownloadUrl(docId)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("Unauthorized: Access denied.", exception.getMessage());
        verify(documentService, never()).getDownloadAccess(any(), any());
    }

    @Test
    void getDownloadUrl_AsUser_Success() {
        UUID docId = UUID.randomUUID();
        DocumentAccessResponse mockResponse = DocumentAccessResponse.builder()
                .documentId(docId)
                .title("Download Doc")
                .fileType("pdf")
                .fileSizeBytes(100L)
                .presignedUrl("https://presigned-download-url")
                .build();

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.setContext(securityContext);

        when(documentService.getDownloadAccess(eq(docId), eq(userDetails))).thenReturn(mockResponse);

        ApiResponse<DocumentAccessResponse> response = documentController.getDownloadUrl(docId);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("https://presigned-download-url", response.getData().getPresignedUrl());

        verify(documentService, times(1)).getDownloadAccess(eq(docId), eq(userDetails));
    }
}
