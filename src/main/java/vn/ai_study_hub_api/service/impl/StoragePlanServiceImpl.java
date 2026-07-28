package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.request.StoragePlanRequest;
import vn.ai_study_hub_api.controller.response.StoragePlanResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.service.StoragePlanService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoragePlanServiceImpl implements StoragePlanService {

    private final StoragePlanRepository storagePlanRepository;

    @Override
    @Transactional(readOnly = true)
    public List<StoragePlanResponse> getAllPlans() {
        log.info("Fetching all storage plans");
        List<StoragePlanEntity> plans = storagePlanRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        return plans.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StoragePlanResponse createPlan(StoragePlanRequest request) {
        log.info("Creating storage plan with name: '{}'", request.getName());
        StoragePlanEntity plan = StoragePlanEntity.builder()
                .name(request.getName().trim())
                .price(request.getPrice())
                .storageLimit(request.getStorageLimit())
                .maxAiRequestsPerDay(request.getMaxAiRequestsPerDay())
                .build();
        // IDENTITY strategy: the DB assigns the id, so a freshly created plan never
        // collides with the seeded free plan (id = 1).
        StoragePlanEntity saved = storagePlanRepository.save(plan);
        log.info("Created storage plan id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public StoragePlanResponse updatePlan(Integer id, StoragePlanRequest request) {
        log.info("Updating storage plan id: {}", id);
        StoragePlanEntity plan = storagePlanRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Storage plan not found with ID: " + id));

        plan.setName(request.getName().trim());
        plan.setPrice(request.getPrice());
        plan.setStorageLimit(request.getStorageLimit());
        plan.setMaxAiRequestsPerDay(request.getMaxAiRequestsPerDay());

        StoragePlanEntity saved = storagePlanRepository.save(plan);
        log.info("Updated storage plan id: {}", saved.getId());
        return toResponse(saved);
    }

    private StoragePlanResponse toResponse(StoragePlanEntity plan) {
        return StoragePlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .price(plan.getPrice())
                .storageLimit(plan.getStorageLimit())
                .maxAiRequestsPerDay(plan.getMaxAiRequestsPerDay())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
