package com.xinyu.ecommerce.common.constant;

public class MqConstants {

    public static final String ORDER_TOPIC = "order_topic";
    public static final String ORDER_TAG_CREATE = "order_create";
    public static final String ORDER_TAG_CLOSE = "order_close";
    public static final String ORDER_TAG_PAY_SUCCESS = "order_pay_success";

    public static final String PAY_TOPIC = "pay_topic";
    public static final String PAY_TAG_CALLBACK = "pay_callback";

    public static final String EMAIL_TOPIC = "email_topic";
    public static final String EMAIL_TAG_SMS = "email_sms";
    public static final String EMAIL_TAG_NOTIFY = "email_notify";

    public static final String STORE_TOPIC = "store_topic";
    public static final String STORE_TAG_STOCK_ROLLBACK = "store_stock_rollback";

    private MqConstants() {
    }
}
