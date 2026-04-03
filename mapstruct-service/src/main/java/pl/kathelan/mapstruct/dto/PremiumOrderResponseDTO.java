package pl.kathelan.mapstruct.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class PremiumOrderResponseDTO extends OrderResponseDTO {
    private String premiumBenefits;
    private int priorityLevel;
    private String tierLabel;        // obliczane: "GOLD", "SILVER", "BRONZE"
}
