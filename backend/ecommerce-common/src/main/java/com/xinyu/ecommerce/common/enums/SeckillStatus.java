package com.xinyu.ecommerce.common.enums;

public enum SeckillStatus {
    NOT_STARTED(0, "未开始"),
    STARTED(1, "抢购中"),
    ENDED(2, "已结束");

    private final int code;
    private final String desc;

    SeckillStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
