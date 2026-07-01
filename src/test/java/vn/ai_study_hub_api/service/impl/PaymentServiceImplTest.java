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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
                .maxAiRequestsPerDay(500)
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
}
