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
import vn.ai_study_hub_api.controller.response.ReportedDocumentResponse;
import vn.ai_study_hub_api.controller.response.ReportDetailResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.*;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.ReportRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.repository.ViolationHistoryRepository;

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

    @Mock
    private ViolationHistoryRepository violationHistoryRepository;

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

    @Test
    void resolveReport_Success() {
        mockDocument.setLinkShare("doc-123456");
        ReportEntity pendingReport = ReportEntity.builder()
                .id(UUID.randomUUID())
                .reporter(adminUser)
                .document(mockDocument)
                .reason("Test reason")
                .status(ReportStatus.PENDING)
                .build();

        when(reportRepository.findById(pendingReport.getId())).thenReturn(Optional.of(pendingReport));

        reportService.resolveReport(pendingReport.getId(), "Custom reason for violation");

        assertEquals(ReportStatus.RESOLVED, pendingReport.getStatus());
        assertEquals(DocumentStatus.DELETED, mockDocument.getStatus());
        assertNotNull(mockDocument.getDeletedAt());
        assertNull(mockDocument.getLinkShare());
        assertEquals(DocumentStatus.COMPLETED, mockDocument.getStatusBeforeDeletion());
        assertTrue(mockDocument.getDeletedByAdmin());

        verify(reportRepository, times(1)).save(pendingReport);
        verify(documentRepository, times(1)).save(mockDocument);
        verify(userRepository, times(1)).save(mockUser);
        verify(violationHistoryRepository, times(1)).save(any(ViolationHistoryEntity.class));
        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
    }

    @Test
    void rejectReport_Success() {
        ReportEntity pendingReport = ReportEntity.builder()
                .id(UUID.randomUUID())
                .reporter(adminUser)
                .document(mockDocument)
                .reason("Test reason")
                .status(ReportStatus.PENDING)
                .build();

        when(reportRepository.findById(pendingReport.getId())).thenReturn(Optional.of(pendingReport));

        reportService.rejectReport(pendingReport.getId());

        assertEquals(ReportStatus.REJECTED, pendingReport.getStatus());
        verify(reportRepository, times(1)).save(pendingReport);
        verify(documentRepository, never()).save(any());
        verify(violationHistoryRepository, never()).save(any());
    }

    @Test
    void getReportedDocuments_Success() {
        ReportedDocumentResponse mockSummary = ReportedDocumentResponse.builder()
                .documentId(documentId)
                .title("test.pdf")
                .uploaderName("Test User")
                .reportCount(5L)
                .latestReportAt(java.time.LocalDateTime.now())
                .build();

        when(reportRepository.findReportedDocumentsSummary(ReportStatus.PENDING)).thenReturn(List.of(mockSummary));

        List<ReportedDocumentResponse> result = reportService.getReportedDocuments();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(documentId, result.get(0).getDocumentId());
        assertEquals("test.pdf", result.get(0).getTitle());
        assertEquals(5L, result.get(0).getReportCount());
        verify(reportRepository, times(1)).findReportedDocumentsSummary(ReportStatus.PENDING);
    }

    @Test
    void getReportDetailsForDocument_Success() {
        ReportEntity mockReport = ReportEntity.builder()
                .id(UUID.randomUUID())
                .reporter(mockUser)
                .document(mockDocument)
                .reason("Test reason")
                .status(ReportStatus.PENDING)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        when(documentRepository.existsById(documentId)).thenReturn(true);
        when(reportRepository.findReportsByDocumentIdAndStatus(documentId, ReportStatus.PENDING)).thenReturn(List.of(mockReport));

        List<ReportDetailResponse> result = reportService.getReportDetailsForDocument(documentId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(mockReport.getId(), result.get(0).getReportId());
        assertEquals("Test User", result.get(0).getReporterName());
        assertEquals("Test reason", result.get(0).getReason());
        verify(documentRepository, times(1)).existsById(documentId);
        verify(reportRepository, times(1)).findReportsByDocumentIdAndStatus(documentId, ReportStatus.PENDING);
    }

    @Test
    void getReportDetailsForDocument_DocumentNotFound() {
        when(documentRepository.existsById(documentId)).thenReturn(false);

        AppException exception = assertThrows(AppException.class, () ->
                reportService.getReportDetailsForDocument(documentId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("The document you are looking for does not exist.", exception.getMessage());
        verify(reportRepository, never()).findReportsByDocumentIdAndStatus(any(), any());
    }
}
