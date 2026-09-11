package com.printcalculator.dto;

import lombok.Data;
import jakarta.validation.constraints.AssertTrue;

@Data
public class CreateOrderRequest {
    @jakarta.validation.constraints.DecimalMin("0.00")
    @jakarta.validation.constraints.Digits(integer = 10, fraction = 2)
    private java.math.BigDecimal expectedShippingCostChf;
    private CustomerDto customer;
    private AddressDto billingAddress;
    private AddressDto shippingAddress;
    private String language;
    private boolean shippingSameAsBilling;

    @AssertTrue(message = "L'accettazione dei Termini e Condizioni e obbligatoria.")
    private boolean acceptTerms;

    @AssertTrue(message = "L'accettazione dell'Informativa Privacy e obbligatoria.")
    private boolean acceptPrivacy;
}
