package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.service.ReportService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Admin Reports", description = "Endpoints for admin to handle document reports")
public class AdminReportController {

    private final ReportService reportService;

    @PostMapping("/{reportId}/resolve")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Resolve a report and delete the document", description = "Updates report status to RESOLVED, deletes the document, logs a violation, and sends a warning notification.")
    public ApiResponse<Void> resolveReport(
            @PathVariable UUID reportId,
            @RequestBody(required = false) Map<String, String> body) {
        String customReason = body != null ? body.get("reason") : null;
        reportService.resolveReport(reportId, customReason);
        return ApiResponse.success(null, "Report resolved and document deleted successfully.");
    }

    @PostMapping("/{reportId}/reject")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reject/Dismiss a report", description = "Updates report status to REJECTED. The document remains active.")
    public ApiResponse<Void> rejectReport(@PathVariable UUID reportId) {
        reportService.rejectReport(reportId);
        return ApiResponse.success(null, "Report rejected successfully. The document remains active.");
    }
}
