package pl.kathelan.soap.push.exception;

public class DeliveryNotFoundException extends RuntimeException {

    public DeliveryNotFoundException(String deliveryId) {
        super("Delivery not found: '%s'".formatted(deliveryId));
    }
}