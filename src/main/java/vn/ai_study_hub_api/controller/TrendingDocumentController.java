package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.TrendingDocumentResponse;
import vn.ai_study_hub_api.service.TrendingDocumentService;

@RestController
@RequestMapping("/api/v1/auth/documents")
@RequiredArgsConstructor
@Tag(name = "Trending Documents", description = "Endpoints for viewing trending documents")
public class TrendingDocumentController {

    private final TrendingDocumentService trendingDocumentService;

    @GetMapping("/trending")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "View trending documents", description = "Retrieve public documents sorted by popularity (rating and reviews).")
    public ApiResponse<Page<TrendingDocumentResponse>> getTrendingDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TrendingDocumentResponse> trendingDocuments = trendingDocumentService.getTrendingDocuments(pageable);
        return ApiResponse.success(trendingDocuments, "Trending documents retrieved successfully");
    }
}
