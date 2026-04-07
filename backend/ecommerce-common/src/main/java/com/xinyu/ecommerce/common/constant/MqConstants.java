package com.xinyu.ecommerce.common.constant;

public class MqConstants {
    public static final String TOPIC_SECKILL_ORDER = "seckill-order";
    public static final String GROUP_ID_ORDER = "order-consumer-group";

    public static final String TOPIC_PAY_SUCCESS = "pay-success";
    public static final String GROUP_ID_PAY_SUCCESS = "order-pay-consumer-group";

    public static final String TOPIC_STOCK_ROLLBACK = "stock-rollback";
    public static final String GROUP_ID_STOCK_ROLLBACK = "store-rollback-consumer-group";

    public static final String TOPIC_ORDER_NOTIFY = "order-notify";
    public static final String GROUP_ID_ORDER_NOTIFY = "email-notify-consumer-group";

    public static final String TOPIC_ORDER_DELAY = "order-delay";
    public static final String GROUP_ID_ORDER_DELAY = "order-delay-consumer-group";

    public static final String TOPIC_PAY_CONFIRMED = "pay-confirmed";
    public static final String GROUP_ID_PAY_CONFIRMED = "store-pay-consumer-group";

    public static final String TAG_PAY = "pay";
    public static final String TAG_ROLLBACK = "rollback";
    public static final String TAG_NOTIFY = "notify";
    public static final String TAG_DELAY = "delay";
}
