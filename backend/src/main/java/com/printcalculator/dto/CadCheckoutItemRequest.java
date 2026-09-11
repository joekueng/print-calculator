package com.printcalculator.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CadCheckoutItemRequest(@NotNull @Min(1) Integer quantity,
                                     @NotNull @Min(1) Long filamentVariantId) {}
