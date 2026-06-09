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
import vn.ai_study_hub_api.controller.request.ReviewRequest;
import vn.ai_study_hub_api.controller.response.ReviewResponse;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.ReviewService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Endpoints for study document management")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/{documentId}/reviews")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Submit a review for a document",
            description = "Allows an authenticated user to rate and comment on a public approved document. Rating must be between 1 and 5."
    )
    public ApiResponse<ReviewResponse> submitReview(
            @PathVariable UUID documentId,
            @Valid @RequestBody ReviewRequest request) {

        UUID userId = getCurrentUserId();
        ReviewResponse response = reviewService.submitReview(documentId, userId, request);
        return ApiResponse.success(response, "Your review has been submitted successfully.");
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }
}
