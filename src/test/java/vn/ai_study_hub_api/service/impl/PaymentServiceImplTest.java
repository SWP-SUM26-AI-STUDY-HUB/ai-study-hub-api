package vn.ai_study_hub_api.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.ai_study_hub_api.model.InvoiceEntity;
import vn.ai_study_hub_api.model.InvoiceStatus;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.repository.InvoiceRepository;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.UserRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Arrays;
import vn.ai_study_hub_api.controller.response.TransactionHistoryResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceImplTest {

    @Mock
    private StoragePlanRepository storagePlanRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(paymentService, "tmnCode", "UWUCKV7J");
        ReflectionTestUtils.setField(paymentService, "hashSecret", "PKBMX9FZP5GGBMF31R9WWV4TQKNK2J2P");
        ReflectionTestUtils.setField(paymentService, "payUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(paymentService, "returnUrl", "http://localhost:8080/api/v1/payments/vnpay-callback");
        ReflectionTestUtils.setField(paymentService, "planDurationHours", 720);
    }

    @Test
    public void testCreatePaymentUrl() {
        UUID userId = UUID.randomUUID();
        Integer planId = 2;

        StoragePlanEntity plan = StoragePlanEntity.builder()
                .id(planId)
                .name("Premium")
                .price(new BigDecimal("200000"))
                .storageLimit(10L)
                .maxAiRequestsPerDay(60)
                .build();

        InvoiceEntity invoice = InvoiceEntity.builder()
                .id(UUID.fromString("11111111-2222-3333-4444-555555555555"))
                .userId(userId)
                .planId(planId)
                .amount(plan.getPrice())
                .status(InvoiceStatus.PENDING)
                .build();

        when(storagePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(invoiceRepository.save(any(InvoiceEntity.class))).thenReturn(invoice);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-FORWARDED-FOR")).thenReturn("127.0.0.1");

        String paymentUrl = paymentService.createPaymentUrl(userId, planId, request);
        System.out.println("==================================================");
        System.out.println("GENERATED PAYMENT URL: " + paymentUrl);
        System.out.println("==================================================");
    }

    @Test
    public void testGetTransactionHistory() {
        UUID userId = UUID.randomUUID();
        
        StoragePlanEntity plan1 = StoragePlanEntity.builder()
                .id(1)
                .name("Basic")
                .price(new BigDecimal("100000"))
                .build();
        StoragePlanEntity plan2 = StoragePlanEntity.builder()
                .id(2)
                .name("Premium")
                .price(new BigDecimal("200000"))
                .build();
                
        InvoiceEntity invoice1 = InvoiceEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .planId(1)
                .amount(new BigDecimal("100000"))
                .provider("VNPAY")
                .transactionId("VNP12345")
                .status(InvoiceStatus.SUCCESS)
                .createdAt(LocalDateTime.of(2026, 7, 10, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 7, 10, 10, 5))
                .build();
                
        InvoiceEntity invoice2 = InvoiceEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .planId(2)
                .amount(new BigDecimal("200000"))
                .provider("VNPAY")
                .transactionId("VNP67890")
                .status(InvoiceStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 7, 10, 11, 0))
                .updatedAt(LocalDateTime.of(2026, 7, 10, 11, 0))
                .build();

        when(storagePlanRepository.findAll()).thenReturn(Arrays.asList(plan1, plan2));
        when(invoiceRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Arrays.asList(invoice2, invoice1));

        List<TransactionHistoryResponse> history = paymentService.getTransactionHistory(userId);

        assertNotNull(history);
        assertEquals(2, history.size());

        TransactionHistoryResponse res1 = history.get(0);
        assertEquals(invoice2.getId(), res1.getId());
        assertEquals("VNP67890", res1.getTransactionId());
        assertEquals(new BigDecimal("200000"), res1.getAmount());
        assertEquals(InvoiceStatus.PENDING, res1.getStatus());
        assertEquals("VNPAY", res1.getProvider());
        assertEquals("Thanh toán nâng cấp tài khoản - Gói Premium", res1.getContent());
        assertEquals(invoice2.getCreatedAt(), res1.getCreatedAt());

        TransactionHistoryResponse res2 = history.get(1);
        assertEquals(invoice1.getId(), res2.getId());
        assertEquals("VNP12345", res2.getTransactionId());
        assertEquals(new BigDecimal("100000"), res2.getAmount());
        assertEquals(InvoiceStatus.SUCCESS, res2.getStatus());
        assertEquals("VNPAY", res2.getProvider());
        assertEquals("Thanh toán nâng cấp tài khoản - Gói Basic", res2.getContent());
        assertEquals(invoice1.getCreatedAt(), res2.getCreatedAt());
        
        verify(storagePlanRepository).findAll();
        verify(invoiceRepository).findAllByUserIdOrderByCreatedAtDesc(userId);
    }
}
