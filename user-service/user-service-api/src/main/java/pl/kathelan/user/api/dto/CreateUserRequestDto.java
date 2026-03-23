package pl.kathelan.user.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.kathelan.common.validation.ValidName;

public record CreateUserRequestDto(
        @ValidName @NotBlank String firstName,
        @ValidName @NotBlank String lastName,
        @Email @NotBlank String email,
        @Valid @NotNull AddressDto address
) {}