package pl.kathelan.auth.exception;

public class AllDevicesBlockedException extends RuntimeException {

    public AllDevicesBlockedException(String userId) {
        super("All devices are blocked for user: " + userId);
    }
}
