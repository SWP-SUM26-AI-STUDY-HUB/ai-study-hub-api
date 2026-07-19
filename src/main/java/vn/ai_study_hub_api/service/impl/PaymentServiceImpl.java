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
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserStatus;
import vn.ai_study_hub_api.model.NotificationEntity;
import vn.ai_study_hub_api.repository.InvoiceRepository;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.PaymentService;
import vn.ai_study_hub_api.controller.response.TransactionHistoryResponse;

import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final StoragePlanRepository storagePlanRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @Value("${vnpay.tmn-code}")
    private String tmnCode;

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    @Value("${vnpay.pay-url}")
    private String payUrl;

    @Value("${vnpay.return-url}")
    private String returnUrl;
    @Value("${app.plan.duration-hours:720}")
    private Integer planDurationHours;

    @Override
    @Transactional
    public String createPaymentUrl(UUID userId, Integer planId, HttpServletRequest request) {
        log.info("Creating payment URL for user: {} and plan: {}", userId, planId);

        // 1. Lấy Storage Plan từ database
        StoragePlanEntity plan = storagePlanRepository.findById(planId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Storage plan not found"));

        if (plan.getPrice() != null && plan.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot initiate payment for a free plan.");
        }

        // 2. tạo hóa đơn đang cờ xử lý trong db
        InvoiceEntity invoice = InvoiceEntity.builder()
                .userId(userId)
                .planId(plan.getId())
                .amount(plan.getPrice())
                .provider("VNPAY")
                .status(InvoiceStatus.PENDING)
                .durationHours(planDurationHours)
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

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        // 4. sắp xếp thuộc tính và tính toán bằng HMAC-SHA512
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        List<String> queryParts = new ArrayList<>();
        List<String> hashParts = new ArrayList<>();
        
        for (String fieldName : fieldNames) {
            String fieldValue = vnp_Params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.UTF_8);
                String encodedName = URLEncoder.encode(fieldName, StandardCharsets.UTF_8);
                
                queryParts.add(encodedName + "=" + encodedValue);
                hashParts.add(fieldName + "=" + encodedValue);
            }
        }
        
        String queryUrl = String.join("&", queryParts);
        String hashData = String.join("&", hashParts);
        String vnp_SecureHash = VNPayUtil.hmacSHA512(hashSecret, hashData);
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        
        String finalPaymentUrl = payUrl + "?" + queryUrl;
        log.info("Successfully generated VNPAY URL: {}", finalPaymentUrl);
        return finalPaymentUrl;
    }

    @Override
    @Transactional
    public Map<String, String> processVnpayIpn(Map<String, String> queryParams) {
        log.info("Received VNPAY IPN callback with params: {}", queryParams);
        Map<String, String> response = new HashMap<>();

        // 1. Kiểm tra chữ ký checksum
        boolean isValidChecksum = VNPayUtil.verifyIpnChecksum(queryParams, hashSecret);
        if (!isValidChecksum) {
            log.warn("Invalid VNPAY IPN checksum");
            response.put("RspCode", "97");
            response.put("Message", "Invalid Checksum");
            return response;
        }

        String vnp_TxnRef = queryParams.get("vnp_TxnRef");
        String vnp_AmountStr = queryParams.get("vnp_Amount");
        String vnp_ResponseCode = queryParams.get("vnp_ResponseCode");
        String vnp_TransactionNo = queryParams.get("vnp_TransactionNo");

        // 2. Tìm kiếm Hóa đơn trong DB
        UUID invoiceId;
        try {
            invoiceId = UUID.fromString(vnp_TxnRef);
        } catch (Exception e) {
            log.warn("Invalid invoice ID format: {}", vnp_TxnRef);
            response.put("RspCode", "01");
            response.put("Message", "Order not found");
            return response;
        }

        Optional<InvoiceEntity> invoiceOpt = invoiceRepository.findByIdForUpdate(invoiceId);
        if (invoiceOpt.isEmpty()) {
            log.warn("Invoice not found with ID: {}", invoiceId);
            response.put("RspCode", "01");
            response.put("Message", "Order not found");
            return response;
        }

        InvoiceEntity invoice = invoiceOpt.get();

        // 3. Kiểm tra số tiền hợp lệ (VNPay gửi amount * 100)
        if (vnp_AmountStr != null) {
            try {
                long vnpAmount = Long.parseLong(vnp_AmountStr);
                long expectedAmount = invoice.getAmount().longValue() * 100;
                if (vnpAmount != expectedAmount) {
                    log.warn("Invalid amount for invoice {}: expected {}, got {}", invoiceId, expectedAmount, vnpAmount);
                    response.put("RspCode", "04");
                    response.put("Message", "Invalid amount");
                    return response;
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid amount format in VNPAY IPN: {}", vnp_AmountStr);
                response.put("RspCode", "04");
                response.put("Message", "Invalid amount");
                return response;
            }
        }

        // 4. Kiểm tra trạng thái hóa đơn (tránh xử lý trùng lặp - Idempotency)
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            log.info("Invoice {} already confirmed with status: {}", invoiceId, invoice.getStatus());
            response.put("RspCode", "02");
            response.put("Message", "Order already confirmed");
            return response;
        }

        // 5. Cập nhật giao dịch và tài khoản người dùng
        if ("00".equals(vnp_ResponseCode)) {
            invoice.setStatus(InvoiceStatus.SUCCESS);
            invoice.setTransactionId(vnp_TransactionNo);
            invoiceRepository.save(invoice);

            UserEntity user = userRepository.findById(invoice.getUserId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

            user.setPlanId(invoice.getPlanId());

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            long duration = invoice.getDurationHours() != null ? invoice.getDurationHours() : 720;
            if (user.getPlanExpiresAt() != null && user.getPlanExpiresAt().isAfter(now)) {
                user.setPlanExpiresAt(user.getPlanExpiresAt().plusHours(duration));
            } else {
                user.setPlanExpiresAt(now.plusHours(duration));
            }

            if (user.getStatus() == UserStatus.OVERLIMITSTORAGE) {
                user.setStatus(UserStatus.ACTIVE);
            }

            userRepository.save(user);
            log.info("Successfully updated invoice {} and upgraded user {} to plan {}", invoiceId, user.getId(), invoice.getPlanId());

            // Lưu thông báo thành công cho người dùng
            StoragePlanEntity upgradedPlan = storagePlanRepository.findById(invoice.getPlanId()).orElse(null);
            String planName = upgradedPlan != null ? upgradedPlan.getName() : "Premium";
            
            notificationRepository.save(NotificationEntity.builder()
                    .user(user)
                    .title("Nâng cấp gói cước thành công")
                    .content("Chúc mừng! Tài khoản của bạn đã được nâng cấp lên gói " + planName + " thành công. Hạn sử dụng của bạn là đến " + user.getPlanExpiresAt())
                    .type("PLAN_UPGRADED")
                    .targetId(invoice.getPlanId() != null ? invoice.getPlanId().toString() : "Premium")
                    .isRead(false)
                    .build());
        } else {
            invoice.setStatus(InvoiceStatus.FAILED);
            invoice.setTransactionId(vnp_TransactionNo);
            invoiceRepository.save(invoice);
            log.info("Invoice {} payment failed with response code {}", invoiceId, vnp_ResponseCode);
        }

        response.put("RspCode", "00");
        response.put("Message", "Confirm Success");
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionHistoryResponse> getTransactionHistory(UUID userId) {
        log.info("Getting transaction history for user: {}", userId);
        
        List<StoragePlanEntity> plans = storagePlanRepository.findAll();
        Map<Integer, String> planNameMap = plans.stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(StoragePlanEntity::getId, StoragePlanEntity::getName, (a, b) -> a));

        List<InvoiceEntity> invoices = invoiceRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

        return invoices.stream().map(invoice -> {
            String planName = planNameMap.getOrDefault(invoice.getPlanId(), "Premium");
            String content = "Thanh toán nâng cấp tài khoản - Gói " + planName;
            
            return TransactionHistoryResponse.builder()
                    .id(invoice.getId())
                    .transactionId(invoice.getTransactionId())
                    .amount(invoice.getAmount())
                    .status(invoice.getStatus())
                    .provider(invoice.getProvider())
                    .content(content)
                    .createdAt(invoice.getCreatedAt() != null ? invoice.getCreatedAt() : LocalDateTime.now())
                    .updatedAt(invoice.getUpdatedAt() != null ? invoice.getUpdatedAt() : LocalDateTime.now())
                    .build();
        }).collect(Collectors.toList());
    }
}
