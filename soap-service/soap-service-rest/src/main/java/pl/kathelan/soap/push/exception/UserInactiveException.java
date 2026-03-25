package pl.kathelan.soap.push.exception;

public class UserInactiveException extends RuntimeException {

    public UserInactiveException(String userId) {
        super("User is inactive: '%s'".formatted(userId));
    }
}