package com.xinyu.ecommerce.pay.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayRequest {
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
}
