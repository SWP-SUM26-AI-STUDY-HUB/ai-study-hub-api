package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import vn.ai_study_hub_api.controller.request.StoragePlanRequest;
import vn.ai_study_hub_api.controller.response.StoragePlanResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.repository.StoragePlanRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StoragePlanServiceImplTest {

    @Mock
    private StoragePlanRepository storagePlanRepository;

    @InjectMocks
    private StoragePlanServiceImpl storagePlanService;

    private StoragePlanRequest sampleRequest() {
        return StoragePlanRequest.builder()
                .name("  Premium  ")
                .price(new BigDecimal("99000.00"))
                .storageLimit(10L * 1024L * 1024L * 1024L)
                .maxAiRequestsPerDay(50)
                .build();
    }

    @Test
    void getAllPlans_Success() {
        StoragePlanEntity free = StoragePlanEntity.builder()
                .id(1).name("Free").price(BigDecimal.ZERO)
                .storageLimit(2L * 1024L * 1024L * 1024L).maxAiRequestsPerDay(5).build();
        StoragePlanEntity premium = StoragePlanEntity.builder()
                .id(2).name("Premium").price(new BigDecimal("99000.00"))
                .storageLimit(10L * 1024L * 1024L * 1024L).maxAiRequestsPerDay(50).build();

        when(storagePlanRepository.findAll(any(Sort.class))).thenReturn(List.of(free, premium));

        List<StoragePlanResponse> results = storagePlanService.getAllPlans();

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).getId());
        assertEquals("Free", results.get(0).getName());
        assertEquals(2, results.get(1).getId());
        assertEquals("Premium", results.get(1).getName());
        verify(storagePlanRepository, times(1)).findAll(any(Sort.class));
    }

    @Test
    void createPlan_Success() {
        StoragePlanRequest request = sampleRequest();
        when(storagePlanRepository.save(any(StoragePlanEntity.class))).thenAnswer(invocation -> {
            StoragePlanEntity arg = invocation.getArgument(0);
            arg.setId(3); // DB-assigned IDENTITY id
            return arg;
        });

        StoragePlanResponse response = storagePlanService.createPlan(request);

        assertNotNull(response);
        assertEquals(3, response.getId());
        // name is trimmed
        assertEquals("Premium", response.getName());
        assertEquals(new BigDecimal("99000.00"), response.getPrice());
        assertEquals(10L * 1024L * 1024L * 1024L, response.getStorageLimit());
        assertEquals(50, response.getMaxAiRequestsPerDay());
        verify(storagePlanRepository, times(1)).save(any(StoragePlanEntity.class));
    }

    @Test
    void updatePlan_Success() {
        Integer id = 1;
        StoragePlanEntity existing = StoragePlanEntity.builder()
                .id(id).name("Free").price(BigDecimal.ZERO)
                .storageLimit(2L * 1024L * 1024L * 1024L).maxAiRequestsPerDay(5).build();
        when(storagePlanRepository.findById(id)).thenReturn(Optional.of(existing));
        when(storagePlanRepository.save(any(StoragePlanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoragePlanResponse response = storagePlanService.updatePlan(id, sampleRequest());

        assertNotNull(response);
        assertEquals(id, response.getId());
        assertEquals("Premium", response.getName());
        assertEquals(new BigDecimal("99000.00"), response.getPrice());
        assertEquals(10L * 1024L * 1024L * 1024L, response.getStorageLimit());
        assertEquals(50, response.getMaxAiRequestsPerDay());
        verify(storagePlanRepository, times(1)).findById(id);
        verify(storagePlanRepository, times(1)).save(any(StoragePlanEntity.class));
    }

    @Test
    void updatePlan_NotFound_ThrowsAppException() {
        Integer id = 999;
        when(storagePlanRepository.findById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> storagePlanService.updatePlan(id, sampleRequest()));

        assertTrue(ex.getMessage().contains("Storage plan not found"));
        verify(storagePlanRepository, times(1)).findById(id);
        verify(storagePlanRepository, never()).save(any(StoragePlanEntity.class));
    }
}
