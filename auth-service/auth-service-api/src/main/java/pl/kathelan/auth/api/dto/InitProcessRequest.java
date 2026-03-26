package pl.kathelan.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InitProcessRequest(
        @NotBlank String userId,
        @NotNull AuthMethod preferredMethod
) {}