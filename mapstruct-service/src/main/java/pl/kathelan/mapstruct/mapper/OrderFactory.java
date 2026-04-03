package pl.kathelan.mapstruct.mapper;

import org.mapstruct.ObjectFactory;
import org.mapstruct.TargetType;
import pl.kathelan.mapstruct.dto.OrderResponseDTO;
import pl.kathelan.mapstruct.dto.PremiumOrderResponseDTO;
import pl.kathelan.mapstruct.model.PremiumOrder;

/**
 * FEATURE: @ObjectFactory — kontrola tworzenia instancji target obiektu.
 * MapStruct wywołuje tę fabrykę zamiast konstruktora gdy pasuje sygnatura.
 */
public class OrderFactory {

    /**
     * Fabryka dla PremiumOrder → PremiumOrderResponseDTO.
     * Pozwala ustawić domyślne wartości przed mapowaniem.
     */
    @ObjectFactory
    public PremiumOrderResponseDTO createPremiumDto(PremiumOrder order) {
        PremiumOrderResponseDTO dto = new PremiumOrderResponseDTO();
        // Oblicz tierLabel na podstawie priorityLevel zanim mapper uzupełni resztę
        dto.setTierLabel(switch (order.getPriorityLevel()) {
            case 1 -> "BRONZE";
            case 2 -> "SILVER";
            case 3 -> "GOLD";
            default -> "STANDARD";
        });
        return dto;
    }

    /**
     * Generyczna fabryka dla Order → OrderResponseDTO.
     * Demonstruje @TargetType — MapStruct przekazuje oczekiwany typ.
     */
    @ObjectFactory
    public <T extends OrderResponseDTO> T createOrderDto(@TargetType Class<T> targetType) {
        try {
            return targetType.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate " + targetType.getSimpleName(), e);
        }
    }
}
