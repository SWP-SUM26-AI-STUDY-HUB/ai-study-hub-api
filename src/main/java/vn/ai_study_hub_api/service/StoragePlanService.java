package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.request.StoragePlanRequest;
import vn.ai_study_hub_api.controller.response.StoragePlanResponse;

import java.util.List;

public interface StoragePlanService {

    List<StoragePlanResponse> getAllPlans();

    StoragePlanResponse createPlan(StoragePlanRequest request);

    StoragePlanResponse updatePlan(Integer id, StoragePlanRequest request);
}
