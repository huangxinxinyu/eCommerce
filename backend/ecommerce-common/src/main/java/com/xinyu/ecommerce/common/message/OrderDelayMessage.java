package com.xinyu.ecommerce.common.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderDelayMessage implements Serializable {
    private String orderNo;
    private Long userId;
    private Long timestamp;
}
