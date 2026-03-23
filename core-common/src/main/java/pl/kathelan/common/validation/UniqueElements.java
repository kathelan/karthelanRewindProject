package pl.kathelan.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Waliduje czy kolekcja nie zawiera duplikatów (porównanie przez equals).
 * Null i pusta kolekcja są dozwolone.
 *
 * Przykład użycia: @UniqueElements List<String> cities
 */
@Documented
@Constraint(validatedBy = UniqueElementsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueElements {
    String message() default "must not contain duplicate elements";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
