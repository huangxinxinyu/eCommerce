package com.xinyu.ecommerce.common.constant;

public class RedisConstants {

    public static final String STOCK_KEY_PREFIX = "stock:";
    
    public static final String BUY_LIST_KEY_PREFIX = "buy_list:";
    
    public static final String USER_ORDER_KEY_PREFIX = "user_order:";
    
    public static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    
    public static final String ORDER_LOCK_KEY_PREFIX = "order_lock:";

    private RedisConstants() {
    }
}
