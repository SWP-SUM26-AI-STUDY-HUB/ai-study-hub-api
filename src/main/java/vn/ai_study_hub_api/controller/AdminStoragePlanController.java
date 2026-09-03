package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.request.StoragePlanRequest;
import vn.ai_study_hub_api.controller.response.StoragePlanResponse;
import vn.ai_study_hub_api.service.StoragePlanService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/storage-plans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Storage Plans", description = "Endpoints for admin storage plan management (list / create / edit)")
public class AdminStoragePlanController {

    private final StoragePlanService storagePlanService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "List all storage plans", description = "Retrieves all storage plans ordered by id ascending (the seeded free plan is always id = 1).")
    public ApiResponse<List<StoragePlanResponse>> getAllPlans() {
        log.info("Admin request to list storage plans");
        List<StoragePlanResponse> plans = storagePlanService.getAllPlans();
        return ApiResponse.success(plans, "Storage plans retrieved successfully");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a storage plan", description = "Creates a new storage plan. The id is DB-assigned (IDENTITY), so a new plan never collides with the free plan (id = 1).")
    public ApiResponse<StoragePlanResponse> createPlan(@Valid @RequestBody StoragePlanRequest request) {
        log.info("Admin request to create storage plan with name: '{}'", request.getName());
        StoragePlanResponse response = storagePlanService.createPlan(request);
        return ApiResponse.success(response, "Storage plan created successfully");
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Update a storage plan", description = "Updates an existing storage plan's name, price, storage limit, and daily AI request quota. Returns 404 if the plan does not exist.")
    public ApiResponse<StoragePlanResponse> updatePlan(
            @PathVariable("id") Integer id,
            @Valid @RequestBody StoragePlanRequest request) {
        log.info("Admin request to update storage plan id: {}", id);
        StoragePlanResponse response = storagePlanService.updatePlan(id, request);
        return ApiResponse.success(response, "Storage plan updated successfully");
    }
}
