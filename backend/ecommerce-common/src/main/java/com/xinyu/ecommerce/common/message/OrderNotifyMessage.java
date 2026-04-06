package com.xinyu.ecommerce.common.message;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderNotifyMessage implements Serializable {
    private String orderNo;
    private Long userId;
    private String email;
    private Integer notifyType;
    private BigDecimal amount;

    public static final int TYPE_PAID = 1;
    public static final int TYPE_CLOSED = 2;
    public static final int TYPE_CANCELLED = 3;
}
