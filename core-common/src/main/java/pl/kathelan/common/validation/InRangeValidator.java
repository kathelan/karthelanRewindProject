package pl.kathelan.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class InRangeValidator implements ConstraintValidator<InRange, Number> {

    private long min;
    private long max;

    @Override
    public void initialize(InRange annotation) {
        this.min = annotation.min();
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(Number value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null obsługuje @NotNull
        }
        long v = value.longValue();
        return v >= min && v <= max;
    }
}
