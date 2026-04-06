package com.xinyu.ecommerce.store.dubbo;

import com.xinyu.ecommerce.store.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class StockDubboServiceImpl implements StockDubboService {
    private final SeckillService seckillService;

    @Override
    public boolean confirmStockRollback(String orderNo, Long productId, Long userId, Integer quantity) {
        log.info("Dubbo 确认库存回滚: orderNo={}, productId={}, userId={}, quantity={}",
                orderNo, productId, userId, quantity);
        return seckillService.confirmStockRollback(orderNo, productId, userId, quantity);
    }
}
