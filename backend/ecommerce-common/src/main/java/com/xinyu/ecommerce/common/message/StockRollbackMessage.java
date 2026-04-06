package com.xinyu.ecommerce.common.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class StockRollbackMessage implements Serializable {
    private String orderNo;
    private Long productId;
    private Long userId;
    private Integer quantity;
}
