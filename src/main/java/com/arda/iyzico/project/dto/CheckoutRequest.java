package com.arda.iyzico.project.dto;

import com.arda.iyzico.project.validation.TurkishIdentityNumber;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CheckoutRequest(
        @NotEmpty @Valid List<CheckoutLine> items,

        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String surname,
        @NotBlank @Email String email,
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "^\\+?[0-9][0-9 ()-]{9,19}$", message = "Not a valid phone number.")
        String phone,

        @NotBlank @TurkishIdentityNumber String identityNumber,

        @NotBlank String address,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String country,
        @NotBlank @Size(max = 20) String zipCode
) {
    public record CheckoutLine(
            @NotNull Long itemId,
            @NotNull @Positive Integer quantity
    ) {}
}
