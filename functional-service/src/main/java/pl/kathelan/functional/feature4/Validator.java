package pl.kathelan.functional.feature4;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Feature 4: Reusable, fluent validator built on {@link Predicate}.
 * <p>
 * Rules are collected with {@link #addRule} and evaluated lazily on {@link #validate}.
 * Each failing predicate contributes its error message to {@link ValidationResult}.
 * The validator is immutable between calls — {@link #and} returns a new combined instance.
 * </p>
 *
 * <pre>{@code
 * ValidationResult result = Validator.<User>of()
 *     .addRule(u -> u.getAge() >= 18, "Wiek musi być >= 18")
 *     .addRule(u -> u.getEmail().contains("@"), "Nieprawidłowy email")
 *     .validate(user);
 * }</pre>
 *
 * @param <T> the type of object being validated
 */
public class Validator<T> {

    private final List<RuleEntry<T>> rules;

    private Validator(List<RuleEntry<T>> rules) {
        this.rules = List.copyOf(rules);
    }

    /** Creates a new, empty validator. */
    public static <T> Validator<T> of() {
        return new Validator<>(List.of());
    }

    /**
     * Returns a new {@code Validator} that includes all current rules plus the given one.
     * The original validator is not modified.
     *
     * @param rule         predicate that must return {@code true} for the target to be valid
     * @param errorMessage message added to {@link ValidationResult} when the predicate fails
     * @return new validator instance
     */
    public Validator<T> addRule(Predicate<T> rule, String errorMessage) {
        List<RuleEntry<T>> updated = new ArrayList<>(rules);
        updated.add(new RuleEntry<>(rule, errorMessage));
        return new Validator<>(updated);
    }

    /**
     * Evaluates all rules against {@code target} and returns the aggregated result.
     * All rules are always checked — validation does not short-circuit on first failure.
     *
     * @param target the object to validate
     * @return {@link ValidationResult#success()} when all rules pass,
     *         {@link ValidationResult#failure(List)} with all failing messages otherwise
     */
    public ValidationResult validate(T target) {
        List<String> errors = new ArrayList<>();
        for (RuleEntry<T> entry : rules) {
            if (!entry.rule().test(target)) {
                errors.add(entry.message());
            }
        }
        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * Returns a new {@code Validator} that combines rules from this instance and {@code other}.
     * The combined validator runs all rules from both in order (this first, then other).
     *
     * @param other the validator whose rules are appended
     * @return new validator with all rules from both
     */
    public Validator<T> and(Validator<T> other) {
        List<RuleEntry<T>> combined = new ArrayList<>(this.rules);
        combined.addAll(other.rules);
        return new Validator<>(combined);
    }

    // ---------------------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------------------

    private record RuleEntry<T>(Predicate<T> rule, String message) {
    }
}
