package com.xinyu.ecommerce.store.config;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BloomFilterConfig {

    private static final String PRODUCT_BLOOM_FILTER_KEY = "bloom:product";

    private static final long EXPECTED_INSERTIONS = 10000;

    private static final double FALSE_PROBABILITY = 0.01;

    @Bean
    public RBloomFilter<Long> productBloomFilter(RedissonClient redisson) {
        RBloomFilter<Long> filter = redisson.getBloomFilter(PRODUCT_BLOOM_FILTER_KEY);
        filter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        return filter;
    }
}
