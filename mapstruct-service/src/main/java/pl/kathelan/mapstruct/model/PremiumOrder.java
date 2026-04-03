package pl.kathelan.mapstruct.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class PremiumOrder extends Order {
    private String premiumBenefits;  // np. "Darmowa dostawa, Priorytetowa obsługa"
    private int priorityLevel;       // 1-3
}
