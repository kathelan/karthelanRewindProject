package pl.kathelan.user.api.dto;

public record UserDto(
        String id,
        String firstName,
        String lastName,
        String email,
        AddressDto address
) {}
