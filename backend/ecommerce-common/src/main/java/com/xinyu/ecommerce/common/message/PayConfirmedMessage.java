package com.xinyu.ecommerce.common.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class PayConfirmedMessage implements Serializable {
    private String orderNo;
    private Long productId;
    private Integer quantity;
    private Long timestamp;
}
