package com.autoflow.modules.billing.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {

    private String orderId;
    private BigDecimal amountInr;
    private BigDecimal discountInr;
    private BigDecimal netInr;
    private String currency;
    private String keyId;
    private String planCode;
    private String planName;
}
