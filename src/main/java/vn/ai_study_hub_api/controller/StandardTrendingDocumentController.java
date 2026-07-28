package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.TrendingDocumentResponse;
import vn.ai_study_hub_api.service.TrendingDocumentService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Trending Documents", description = "Endpoints for viewing trending documents")
public class StandardTrendingDocumentController {

    private final TrendingDocumentService trendingDocumentService;

    @GetMapping("/trending")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "View trending documents", description = "Retrieve public documents sorted by popularity (downloads).")
    public ApiResponse<List<TrendingDocumentResponse>> getTrendingDocuments() {
        List<TrendingDocumentResponse> trendingDocuments = trendingDocumentService.getTrendingDocuments();
        return ApiResponse.success(trendingDocuments, "Trending documents retrieved successfully");
    }
}
