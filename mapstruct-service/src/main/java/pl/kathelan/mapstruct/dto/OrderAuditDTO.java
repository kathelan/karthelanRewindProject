package pl.kathelan.mapstruct.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAuditDTO {
    private String orderNumber;
    private String status;
    private String performedBy;    // pochodzi z drugiego parametru źródłowego
    private String auditTimestamp; // defaultExpression gdy createdAt == null
    private String currency;       // defaultValue gdy currency == null
}
