package pl.kathelan.mapstruct.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO demonstrujące mapowanie jednego pola LocalDateTime
 * na dwa osobne pola String: datę i czas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimelineDTO {
    private String orderNumber;
    private String status;
    /** Tylko data z createdAt, format "dd.MM.yyyy" */
    private String createdDate;
    /** Tylko czas z createdAt, format "HH:mm:ss" */
    private String createdTime;
    /** Tylko data z updatedAt, format "dd.MM.yyyy" */
    private String updatedDate;
    /** Tylko czas z updatedAt, format "HH:mm:ss" */
    private String updatedTime;
    /** Pełna data-czas dla audytu, format "yyyy-MM-dd'T'HH:mm:ss" */
    private String isoTimestamp;
}
