package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import vn.ai_study_hub_api.controller.request.ReportRequest;
import vn.ai_study_hub_api.controller.response.ReportResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.*;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.ReportRepository;
import vn.ai_study_hub_api.repository.UserRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReportServiceImplTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private ReportServiceImpl reportService;

    private UserEntity mockUser;
    private UserEntity adminUser;
    private DocumentEntity mockDocument;
    private UUID userId;
    private UUID documentId;
    private ReportRequest reportRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        documentId = UUID.randomUUID();

        mockUser = UserEntity.builder()
                .id(userId)
                .email("testuser@example.com")
                .fullName("Test User")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();

        adminUser = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .fullName("Admin User")
                .role(UserRole.ADMIN)
                .build();

        mockDocument = DocumentEntity.builder()
                .id(documentId)
                .uploader(mockUser)
                .title("test.pdf")
                .fileUrl("documents/mock-key.pdf")
                .fileType("pdf")
                .fileSizeBytes(100L)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PUBLIC)
                .build();

        reportRequest = new ReportRequest();
        reportRequest.setReason("Copyright infringement");
    }

    @Test
    void submitReport_Success() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(userRepository.findAllByRole(UserRole.ADMIN)).thenReturn(List.of(adminUser));
        when(reportRepository.save(any(ReportEntity.class))).thenAnswer(invocation -> {
            ReportEntity r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ReportResponse response = reportService.submitReport(documentId, userId, reportRequest);

        assertNotNull(response);
        assertNotNull(response.getReportId());
        assertEquals(documentId, response.getDocumentId());
        assertEquals("Copyright infringement", response.getReason());
        assertEquals(ReportStatus.PENDING.name(), response.getStatus());
        assertNotNull(response.getCreatedAt());

        verify(reportRepository, times(1)).save(any(ReportEntity.class));
        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
    }

    @Test
    void submitReport_DocumentNotFound() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                reportService.submitReport(documentId, userId, reportRequest)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("The document you are looking for does not exist.", exception.getMessage());
        verify(reportRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void submitReport_PrivateDocumentForbidden() {
        mockDocument.setVisibility(DocumentVisibility.PRIVATE);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));

        AppException exception = assertThrows(AppException.class, () ->
                reportService.submitReport(documentId, userId, reportRequest)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("You can only report public documents.", exception.getMessage());
        verify(reportRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void submitReport_DeletedDocumentBadRequest() {
        mockDocument.setDeletedAt(java.time.LocalDateTime.now());
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));

        AppException exception = assertThrows(AppException.class, () ->
                reportService.submitReport(documentId, userId, reportRequest)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("This document has been deleted and cannot be reported.", exception.getMessage());
        verify(reportRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void submitReport_ReporterNotFound() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                reportService.submitReport(documentId, userId, reportRequest)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("Reporter not found.", exception.getMessage());
        verify(reportRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
    }
}
