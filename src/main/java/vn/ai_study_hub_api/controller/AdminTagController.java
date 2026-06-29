package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.request.AdminCreateTagRequest;
import vn.ai_study_hub_api.controller.response.TagResponse;
import vn.ai_study_hub_api.service.TagService;

@RestController
@RequestMapping("/api/v1/admin/tags")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Tags", description = "Endpoints for admin tag management")
public class AdminTagController {

    private final TagService tagService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create public tag", description = "Creates a new public tag as an administrator and automatically merges any existing private tags with matching label.")
    public ApiResponse<TagResponse> createPublicTag(@Valid @RequestBody AdminCreateTagRequest request) {
        log.info("Admin request to create public tag with label: '{}'", request.getLabel());
        TagResponse response = tagService.createPublicTag(request);
        return ApiResponse.success(response, "Public tag created successfully");
    }
}
