package com.xinyu.ecommerce.store.service;

import com.xinyu.ecommerce.store.entity.Product;
import com.xinyu.ecommerce.store.mapper.ProductMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BloomFilterInitService {

    private final RBloomFilter<Long> productBloomFilter;
    private final ProductMapper productMapper;

    @PostConstruct
    public void init() {
        List<Product> products = productMapper.selectList(null);
        for (Product product : products) {
            productBloomFilter.add(product.getId());
        }
        log.info("布隆过滤器初始化完成，共加载 {} 个商品", products.size());
    }
}
