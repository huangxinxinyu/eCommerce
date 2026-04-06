package com.xinyu.ecommerce.store.controller;

import com.xinyu.ecommerce.common.result.Result;
import com.xinyu.ecommerce.store.entity.Product;
import com.xinyu.ecommerce.store.service.ProductService;
import com.xinyu.ecommerce.store.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreController {
    private final ProductService productService;
    private final SeckillService seckillService;

    @GetMapping("/goods")
    public Result<List<Product>> getGoods() {
        List<Product> products = productService.getAllProducts();
        return Result.success(products);
    }

    @GetMapping("/goods/{id}")
    public Result<Product> getGoodsDetail(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        return Result.success(product);
    }

    @PostMapping("/seckill/order")
    public Result<Map<String, String>> seckillOrder(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("userId").toString());
        Long productId = Long.valueOf(request.get("productId").toString());
        Integer quantity = Integer.valueOf(request.get("quantity").toString());

        String orderNo = seckillService.seckillOrder(userId, productId, quantity);

        Map<String, String> data = new HashMap<>();
        data.put("orderNo", orderNo);
        return Result.success(data);
    }

    @PostMapping("/seckill/rollback")
    public Result<Boolean> rollback(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("userId").toString());
        Long productId = Long.valueOf(request.get("productId").toString());
        Integer quantity = Integer.valueOf(request.get("quantity").toString());
        String orderNo = "MANUAL" + System.currentTimeMillis();

        boolean success = seckillService.rollback(orderNo, userId, productId, quantity);
        return Result.success(success);
    }

    @PostMapping("/init/stock")
    public Result<Boolean> initStock(@RequestBody Map<String, Object> request) {
        Long productId = Long.valueOf(request.get("productId").toString());
        Integer stock = Integer.valueOf(request.get("stock").toString());

        seckillService.initStock(productId, stock);
        return Result.success(true);
    }
}
