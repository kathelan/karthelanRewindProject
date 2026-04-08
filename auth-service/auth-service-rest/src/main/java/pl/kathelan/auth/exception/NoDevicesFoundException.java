package pl.kathelan.auth.exception;

public class NoDevicesFoundException extends RuntimeException {

    public NoDevicesFoundException(String userId) {
        super("No devices found for user: " + userId);
    }
}
