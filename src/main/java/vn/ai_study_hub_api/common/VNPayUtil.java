package vn.ai_study_hub_api.common;

import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class VNPayUtil {

    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new NullPointerException();
            }
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    public static String getIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        if ("0:0:0:0:0:0:0:1".equals(ipAddress) || "::1".equals(ipAddress)) {
            ipAddress = "127.0.0.1";
        }
        return ipAddress;
    }

    public static boolean verifyIpnChecksum(java.util.Map<String, String> fields, String hashSecret) {
        String vnp_SecureHash = fields.get("vnp_SecureHash");
        if (vnp_SecureHash == null || vnp_SecureHash.isEmpty()) {
            return false;
        }
        java.util.Map<String, String> params = new java.util.HashMap<>(fields);
        params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");

        java.util.List<String> fieldNames = new java.util.ArrayList<>(params.keySet());
        java.util.Collections.sort(fieldNames);
        java.util.List<String> queryParts = new java.util.ArrayList<>();
        for (String fieldName : fieldNames) {
            String fieldValue = params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                queryParts.add(fieldName + "=" + java.net.URLEncoder.encode(fieldValue, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        String hashData = String.join("&", queryParts);
        String calculatedHash = hmacSHA512(hashSecret, hashData);
        return calculatedHash.equalsIgnoreCase(vnp_SecureHash);
    }
}
