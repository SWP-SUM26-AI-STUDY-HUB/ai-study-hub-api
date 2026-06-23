package vn.ai_study_hub_api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.ReportedDocumentResponse;
import vn.ai_study_hub_api.controller.response.ReportDetailResponse;
import vn.ai_study_hub_api.service.ReportService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AdminReportControllerTest {

    @Mock
    private ReportService reportService;

    @InjectMocks
    private AdminReportController adminReportController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getReportedDocuments_Success() {
        ReportedDocumentResponse summary = ReportedDocumentResponse.builder()
                .documentId(UUID.randomUUID())
                .title("Reported Book")
                .uploaderName("Alice")
                .reportCount(3L)
                .latestReportAt(LocalDateTime.now())
                .build();

        when(reportService.getReportedDocuments()).thenReturn(List.of(summary));

        ApiResponse<List<ReportedDocumentResponse>> response = adminReportController.getReportedDocuments();

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Reported documents retrieved successfully.", response.getMessage());
        assertEquals(1, response.getData().size());
        assertEquals("Reported Book", response.getData().get(0).getTitle());
        verify(reportService, times(1)).getReportedDocuments();
    }

    @Test
    void getReportDetails_Success() {
        UUID docId = UUID.randomUUID();
        ReportDetailResponse detail = ReportDetailResponse.builder()
                .reportId(UUID.randomUUID())
                .reporterName("Bob")
                .reason("Spam")
                .createdAt(LocalDateTime.now())
                .build();

        when(reportService.getReportDetailsForDocument(docId)).thenReturn(List.of(detail));

        ApiResponse<List<ReportDetailResponse>> response = adminReportController.getReportDetails(docId);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Report details retrieved successfully.", response.getMessage());
        assertEquals(1, response.getData().size());
        assertEquals("Bob", response.getData().get(0).getReporterName());
        verify(reportService, times(1)).getReportDetailsForDocument(docId);
    }

    @Test
    void resolveReport_Success() {
        UUID reportId = UUID.randomUUID();
        Map<String, String> body = new HashMap<>();
        body.put("reason", "Inappropriate content");

        doNothing().when(reportService).resolveReport(reportId, "Inappropriate content");

        ApiResponse<Void> response = adminReportController.resolveReport(reportId, body);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Report resolved and document deleted successfully.", response.getMessage());
        verify(reportService, times(1)).resolveReport(reportId, "Inappropriate content");
    }

    @Test
    void rejectReport_Success() {
        UUID reportId = UUID.randomUUID();

        doNothing().when(reportService).rejectReport(reportId);

        ApiResponse<Void> response = adminReportController.rejectReport(reportId);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Report rejected successfully. The document remains active.", response.getMessage());
        verify(reportService, times(1)).rejectReport(reportId);
    }
}
