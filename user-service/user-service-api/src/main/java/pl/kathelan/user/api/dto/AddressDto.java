package pl.kathelan.user.api.dto;

public record AddressDto(
        String street,
        String city,
        String zipCode,
        String country
) {}
