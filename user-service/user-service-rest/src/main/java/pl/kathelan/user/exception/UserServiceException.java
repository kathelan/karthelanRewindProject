package pl.kathelan.user.exception;

public class UserServiceException extends RuntimeException {

    private final String errorCode;

    public UserServiceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
