package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.request.ReportRequest;
import vn.ai_study_hub_api.controller.response.ReportResponse;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.ReportService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Endpoints for study document management")
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/{documentId}/reports")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Report an abusive document",
            description = "Allows an authenticated user to report a public document for abuse or copyright infringement. The report is saved with status 'pending' and admins are notified."
    )
    public ApiResponse<ReportResponse> submitReport(
            @PathVariable UUID documentId,
            @Valid @RequestBody ReportRequest request) {

        UUID reporterId = getCurrentUserId();
        ReportResponse response = reportService.submitReport(documentId, reporterId, request);
        return ApiResponse.success(response, "Thank you. Your report has been submitted for administrator review.");
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }
}
