package pl.kathelan.soap.user.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String id) {
        super("User with id '%s' not found".formatted(id));
    }
}
