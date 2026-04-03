package pl.kathelan.mapstruct.external;

public enum ExternalStatus {
    NEW,          // = PENDING
    APPROVED,     // = CONFIRMED
    IN_PROGRESS,  // = PROCESSING
    IN_TRANSIT,   // = SHIPPED
    DONE,         // = DELIVERED
    REJECTED      // = CANCELLED
}
