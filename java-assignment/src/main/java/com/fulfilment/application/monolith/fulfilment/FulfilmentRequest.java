package com.fulfilment.application.monolith.fulfilment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The body of POST /fulfilment. */
public record FulfilmentRequest(
    @NotNull Long storeId, @NotNull Long productId, @NotBlank String warehouseBusinessUnitCode) {}
