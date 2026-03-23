package pl.kathelan.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Waliduje imię lub nazwisko: tylko litery (w tym unicode/polskie), spacje, myślniki i apostrofy.
 * Długość: 2–50 znaków. Null jest dozwolony — użyj @NotNull osobno jeśli potrzeba.
 *
 * Przykłady poprawnych: "Jan", "Anne-Marie", "O'Brien", "Józef"
 * Przykłady niepoprawnych: "J", "Jan123", "  "
 */
@Documented
@Constraint(validatedBy = ValidNameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidName {
    String message() default "must contain only letters, spaces, hyphens or apostrophes (2–50 characters)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
