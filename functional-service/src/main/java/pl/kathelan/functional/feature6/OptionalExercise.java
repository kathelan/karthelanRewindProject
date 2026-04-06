package pl.kathelan.functional.feature6;

import pl.kathelan.functional.feature6.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Feature 6: Optional API exercises — static utility methods demonstrating
 * safe null-handling, chaining, and flatMap composition.
 */
public class OptionalExercise {

    private OptionalExercise() {
    }

    /**
     * Returns the first user with age &ge; 18, or {@link Optional#empty()} if none exists.
     *
     * @param users list of users to search
     * @return Optional containing the first adult found
     */
    public static Optional<User> findFirstAdult(List<User> users) {
        return users.stream()
                .filter(u -> u.getAge() >= 18)
                .findFirst();
    }

    /**
     * Maps an {@link Optional} of {@link User} to their email address.
     * Returns {@link Optional#empty()} when the input is empty.
     *
     * @param user optional user
     * @return optional email string
     */
    public static Optional<String> mapToEmail(Optional<User> user) {
        return user.map(User::getEmail);
    }

    /**
     * Returns the user's name if present, otherwise returns {@code defaultName}.
     *
     * @param user        optional user
     * @param defaultName fallback value when the optional is empty
     * @return user name or {@code defaultName}
     */
    public static String getNameOrDefault(Optional<User> user, String defaultName) {
        return user.map(User::getName).orElse(defaultName);
    }

    /**
     * Finds a user by name in the list, then returns their email address.
     * Returns {@link Optional#empty()} when no user with that name exists or when
     * the matched user has no email set.
     *
     * @param users list of users to search
     * @param name  name to look up
     * @return optional email of the matching user
     */
    public static Optional<String> findUserEmail(List<User> users, String name) {
        return users.stream()
                .filter(u -> name.equals(u.getName()))
                .findFirst()
                .flatMap(u -> Optional.ofNullable(u.getEmail()));
    }
}
