package pl.kathelan.soap.client.exception;

public class SoapClientException extends RuntimeException {

    private final String errorCode;

    public SoapClientException(String errorCode, String message) {
        super("SOAP call failed [%s]: %s".formatted(errorCode, message));
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
