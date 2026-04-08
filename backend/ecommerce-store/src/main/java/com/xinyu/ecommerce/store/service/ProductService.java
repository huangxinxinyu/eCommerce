package com.xinyu.ecommerce.store.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.xinyu.ecommerce.store.entity.Product;
import com.xinyu.ecommerce.store.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String PRODUCT_CACHE_KEY_PREFIX = "product:cache:";

    private final ProductMapper productMapper;
    private final RBloomFilter<Long> productBloomFilter;
    private final Cache<Long, Product> productLocalCache;
    private final RedisTemplate<String, Object> redisTemplate;

    public List<Product> getAllProducts() {
        return productMapper.selectList(null);
    }

    public Product getProductById(Long id) {
        if (!productBloomFilter.contains(id)) {
            log.debug("布隆过滤器判断商品不存在，直接返回: productId={}", id);
            return null;
        }

        Product cached = productLocalCache.getIfPresent(id);
        if (cached != null) {
            log.debug("Caffeine 本地缓存命中: productId={}", id);
            return cached;
        }

        String cacheKey = PRODUCT_CACHE_KEY_PREFIX + id;
        Object cachedObj = redisTemplate.opsForValue().get(cacheKey);
        if (cachedObj != null) {
            Product product = (Product) cachedObj;
            log.debug("Redis 缓存命中，回填 Caffeine: productId={}", id);
            productLocalCache.put(id, product);
            return product;
        }

        Product product = productMapper.selectById(id);
        if (product != null) {
            redisTemplate.opsForValue().set(cacheKey, product, 1, TimeUnit.HOURS);
            productLocalCache.put(id, product);
            log.debug("从 MySQL 查询，回填 Redis 和 Caffeine: productId={}", id);
            return product;
        }

        return null;
    }

    public boolean addProduct(Product product) {
        int result = productMapper.insert(product);
        if (result > 0) {
            productBloomFilter.add(product.getId());
            log.info("新增商品并同步布隆过滤器: productId={}", product.getId());
        }
        return result > 0;
    }
}
