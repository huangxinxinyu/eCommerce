package com.xinyu.ecommerce.store.dubbo;

public interface StockDubboService {

    boolean confirmStockRollback(String orderNo, Long productId, Long userId, Integer quantity);
}
