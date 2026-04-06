package pl.kathelan.functional.feature4;

import java.util.List;

/**
 * Feature 4: Result of a validation operation.
 * <p>
 * Immutable value object carrying whether the target passed all rules
 * and the list of error messages for rules that failed.
 * </p>
 */
public record ValidationResult(boolean valid, List<String> errors) {

    /**
     * Canonical constructor — defensively copies the error list.
     */
    public ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = List.copyOf(errors);
    }

    /** Returns {@code true} when all validation rules passed. */
    public boolean isValid() {
        return valid;
    }

    /** Returns an unmodifiable list of error messages (empty when valid). */
    public List<String> getErrors() {
        return errors;
    }

    /** Factory: represents a successful (no errors) validation. */
    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }

    /** Factory: represents a failed validation with the given error messages. */
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors);
    }
}
