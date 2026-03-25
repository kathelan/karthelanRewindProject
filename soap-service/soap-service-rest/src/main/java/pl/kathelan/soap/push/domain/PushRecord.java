package pl.kathelan.soap.push.domain;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.LocalDateTime;

@Value
@Builder
@With
public class PushRecord {
    String deliveryId;
    String userId;
    String processId;
    PushStatus status;
    LocalDateTime createdAt;
}