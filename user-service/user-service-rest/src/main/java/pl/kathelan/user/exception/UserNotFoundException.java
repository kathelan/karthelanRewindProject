package pl.kathelan.user.exception;

public class UserNotFoundException extends UserServiceException {

    public UserNotFoundException(String userId) {
        super("USER_NOT_FOUND", "User not found: " + userId);
    }
}
