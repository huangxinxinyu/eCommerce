package com.xinyu.ecommerce.common.message;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderCreateMessage implements Serializable {
    private String orderNo;
    private Long userId;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private Long timestamp;
}
