package com.xinyu.ecommerce.pay.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayResponse {
    private String payNo;
    private String orderNo;
    private BigDecimal amount;
    private Integer payStatus;
}
