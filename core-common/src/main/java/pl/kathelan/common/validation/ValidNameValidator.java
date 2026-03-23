package pl.kathelan.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class ValidNameValidator implements ConstraintValidator<ValidName, String> {

    private static final Pattern NAME_PATTERN = Pattern.compile("^(?=.*\\p{L})[\\p{L}\\s'\\-]{2,50}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null obsługuje @NotNull
        }
        return NAME_PATTERN.matcher(value).matches();
    }
}
