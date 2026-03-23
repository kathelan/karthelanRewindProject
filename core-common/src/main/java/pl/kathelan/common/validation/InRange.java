package pl.kathelan.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Waliduje czy liczba mieści się w zakresie [min, max] (włącznie).
 * Działa z Integer, Long, Short, Byte i ich prymitywnymi odpowiednikami.
 * Null jest dozwolony — użyj @NotNull osobno jeśli potrzeba.
 *
 * Przykład użycia: @InRange(min = 1, max = 100) int pageSize
 */
@Documented
@Constraint(validatedBy = InRangeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface InRange {
    long min() default Long.MIN_VALUE;
    long max() default Long.MAX_VALUE;
    String message() default "must be between {min} and {max}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
