package pl.kathelan.user.api.dto;

public record CreateUserRequestDto(
        String firstName,
        String lastName,
        String email,
        AddressDto address
) {}
