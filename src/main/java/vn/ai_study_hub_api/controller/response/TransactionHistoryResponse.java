package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.ai_study_hub_api.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHistoryResponse {
    private UUID id;
    private String transactionId;
    private BigDecimal amount;
    private InvoiceStatus status;
    private String provider;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
