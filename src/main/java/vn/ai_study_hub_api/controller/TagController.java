package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.TagResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.TagService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Tags", description = "Endpoints for user personal & public tag management")
public class TagController {

    private final TagService tagService;

    @GetMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Search Accessible Tags (Public + User Private)", description = "Suggests public tags and current authenticated user's private tags matching the keyword for autocompletion.")
    public ApiResponse<List<TagResponse>> searchTags(
            @Parameter(description = "Keyword to search tags", required = true)
            @RequestParam("keyword") String keyword) {
        log.info("Received request to search tags with keyword: '{}'", keyword);
        UUID userId = getCurrentUserId(false);
        List<TagResponse> tags = tagService.searchTags(keyword, userId);
        return ApiResponse.success(tags, "Tags retrieved successfully");
    }

    @GetMapping("/public")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get All Public Tags", description = "Retrieves all public tags for survey/preference selection. Requires authentication.")
    public ApiResponse<List<TagResponse>> getAllPublicTags() {
        log.info("Received request to get all public tags");
        List<TagResponse> tags = tagService.getAllPublicTags();
        return ApiResponse.success(tags, "Public tags retrieved successfully");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Create User Tags (Personal / Private)", description = "Creates private tags for the authenticated user if they do not exist. Returns existing public or user private tags if matching.")
    public ApiResponse<List<TagResponse>> createTags(
            @RequestBody List<String> tags) {
        log.info("Received request to create tags: {}", tags);
        UUID userId = getCurrentUserId(true);
        List<TagResponse> response = tagService.createUserTags(tags, userId);
        return ApiResponse.success(response, "Tags created successfully");
    }


    private UUID getCurrentUserId(boolean required) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            return userDetails.getId();
        }
        if (required) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: User access required");
        }
        return null;
    }
}

