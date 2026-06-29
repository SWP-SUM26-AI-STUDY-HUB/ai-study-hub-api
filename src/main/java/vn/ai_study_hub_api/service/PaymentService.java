package vn.ai_study_hub_api.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

public interface PaymentService {
    String createPaymentUrl(UUID userId, Integer planId, HttpServletRequest request);
    Map<String, String> processVnpayIpn(Map<String, String> queryParams);
}
