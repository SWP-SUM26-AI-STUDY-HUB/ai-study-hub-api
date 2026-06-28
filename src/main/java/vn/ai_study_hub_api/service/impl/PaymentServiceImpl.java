package vn.ai_study_hub_api.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.common.VNPayUtil;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.InvoiceEntity;
import vn.ai_study_hub_api.model.InvoiceStatus;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.repository.InvoiceRepository;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.service.PaymentService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final StoragePlanRepository storagePlanRepository;
    private final InvoiceRepository invoiceRepository;

    @Value("${vnpay.tmn-code}")
    private String tmnCode;

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    @Value("${vnpay.pay-url}")
    private String payUrl;

    @Value("${vnpay.return-url}")
    private String returnUrl;

    @Override
    @Transactional
    public String createPaymentUrl(UUID userId, Integer planId, HttpServletRequest request) {
        log.info("Creating payment URL for user: {} and plan: {}", userId, planId);

        // 1. Lấy Storage Plan từ database
        StoragePlanEntity plan = storagePlanRepository.findById(planId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Storage plan not found"));

        // 2. tạo hóa đơn đang cờ xử lý trong db
        InvoiceEntity invoice = InvoiceEntity.builder()
                .userId(userId)
                .planId(plan.getId())
                .amount(plan.getPrice())
                .provider("VNPAY")
                .status(InvoiceStatus.PENDING)
                .durationDays(30)
                .build();

        invoice = invoiceRepository.save(invoice);
        UUID invoiceId = invoice.getId();
        log.info("Created invoice with ID: {}", invoiceId);

        // 3. tập hợp tham số của API VNpay
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        
        // VNPAY tính tieenf bắng cách nhân 100
        long vnpAmount = plan.getPrice().longValue() * 100;

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", tmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(vnpAmount));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", invoiceId.toString());
        vnp_Params.put("vnp_OrderInfo", "Thanh toan nang cap tai khoan - Hoa don " + invoiceId);
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", returnUrl);
        vnp_Params.put("vnp_IpAddr", VNPayUtil.getIpAddress(request));

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        // 4. sắp xếp thuộc tính và tính toán bằng HMAC-SHA512
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                // Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                
                // Build query string
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        
        String queryUrl = query.toString();
        String vnp_SecureHash = VNPayUtil.hmacSHA512(hashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        
        String finalPaymentUrl = payUrl + "?" + queryUrl;
        log.info("Successfully generated VNPAY URL: {}", finalPaymentUrl);
        return finalPaymentUrl;
    }
}
