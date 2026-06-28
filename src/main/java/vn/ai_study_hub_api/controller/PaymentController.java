package vn.ai_study_hub_api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.ai_study_hub_api.controller.request.PaymentRequest;
import vn.ai_study_hub_api.controller.response.PaymentResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.PaymentService;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/create-payment")
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest paymentRequest,
            Authentication authentication,
            HttpServletRequest request) {

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }

        String paymentUrl = paymentService.createPaymentUrl(
                userDetails.getId(), 
                paymentRequest.getPlanId(), 
                request
        );

        return ResponseEntity.ok(PaymentResponse.builder()
                .paymentUrl(paymentUrl)
                .build());
    }
}
