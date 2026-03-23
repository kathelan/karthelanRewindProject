package pl.kathelan.user.exception;

public class UserAlreadyExistsException extends UserServiceException {

    public UserAlreadyExistsException(String email) {
        super("USER_ALREADY_EXISTS", "User already exists with email: " + email);
    }
}
