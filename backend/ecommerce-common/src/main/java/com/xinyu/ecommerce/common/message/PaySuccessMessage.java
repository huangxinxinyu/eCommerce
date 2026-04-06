package com.xinyu.ecommerce.common.message;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class PaySuccessMessage implements Serializable {
    private String orderNo;
    private String payNo;
    private Long userId;
    private BigDecimal paidAmount;
    private Long payTime;
}
