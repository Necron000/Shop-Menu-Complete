package com.arda.iyzico.project.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateItemRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true, message = "Price must be greater than zero.")
    private BigDecimal price;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;

    @NotNull
    @Min(0)
    private Integer stock;
}
