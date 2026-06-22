package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.TagResponse;
import vn.ai_study_hub_api.service.TagService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tags", description = "Endpoints for tag management")
public class TagController {

    private final TagService tagService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get all tags", description = "Retrieves a list of all tags in the system.")
    public ApiResponse<List<TagResponse>> getAllTags() {
        log.info("Received request to get all tags");
        List<TagResponse> tags = tagService.getAllTags();
        return ApiResponse.success(tags, "All tags retrieved successfully");
    }

    @GetMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Search tags by keyword", description = "Suggests tags matching the keyword (case-insensitive) for auto-completion.")
    public ApiResponse<List<TagResponse>> searchTags(
            @Parameter(description = "Keyword to search tags", required = true)
            @RequestParam("keyword") String keyword) {
        log.info("Received request to search tags with keyword: '{}'", keyword);
        List<TagResponse> tags = tagService.searchTags(keyword);
        return ApiResponse.success(tags, "Tags retrieved successfully");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Create tags", description = "Creates tags in the system if they do not exist. No document association.")
    public ApiResponse<List<TagResponse>> createTags(
            @RequestBody List<String> tags) {
        log.info("Received request to create tags: {}", tags);
        List<TagResponse> response = tagService.createTags(tags);
        return ApiResponse.success(response, "Tags created successfully");
    }
}
