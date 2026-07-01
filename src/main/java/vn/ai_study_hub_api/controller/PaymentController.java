package vn.ai_study_hub_api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

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

    @GetMapping("/vnpay-ipn")
    public ResponseEntity<java.util.Map<String, String>> vnpayIpn(@RequestParam java.util.Map<String, String> queryParams) {
        try {
            java.util.Map<String, String> response = paymentService.processVnpayIpn(queryParams);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            java.util.Map<String, String> errorResponse = new java.util.HashMap<>();
            errorResponse.put("RspCode", "99");
            errorResponse.put("Message", "Internal error: " + e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }

    @GetMapping("/vnpay-callback")
    public org.springframework.web.servlet.view.RedirectView vnpayCallback(@RequestParam java.util.Map<String, String> queryParams) {
        try {
            paymentService.processVnpayIpn(queryParams);
        } catch (Exception e) {
            // Ignore callback exceptions to ensure the user redirect still happens
        }
        String responseCode = queryParams.get("vnp_ResponseCode");
        String targetUrl = frontendUrl + "?paymentStatus=" + ("00".equals(responseCode) ? "success" : "failed");
        return new org.springframework.web.servlet.view.RedirectView(targetUrl);
    }
}
