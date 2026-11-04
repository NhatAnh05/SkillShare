package com.skillrental.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data public class PaymentForm {
    @NotBlank private String method;
    private String couponCode;
    private boolean acceptedTerms;
}
