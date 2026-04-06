package com.xinyu.ecommerce.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_payment")
public class Payment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentNo;
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime updateTime;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_REFUNDED = 3;
}
