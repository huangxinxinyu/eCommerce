# Sprint 3：缓存优化

## 目标

**解决缓存穿透 + 缓存击穿 + 热点数据缓存性能优化**

- 布隆过滤器拦截不存在商品的恶意/错误请求
- Caffeine L1 本地缓存热点商品信息
- 多级缓存架构实现

---

## 方案 A：单机版架构

> 适用于单实例部署，布隆过滤器和 Caffeine 都放在 Store Service

### 缓存分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     请求进入 Store Service                         │
└─────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    1. 布隆过滤器 (Redis)                          │
│                                                                 │
│   作用：拦截不存在商品的请求，防止缓存穿透                        │
│   位置：Store Service → Redis                                   │
│   数据：所有秒杀商品 ID                                          │
│   逻辑：                                                          │
│     - 判断不存在 → 直接返回 null，不查后续                        │
│     - 判断可能存在 → 继续往下走                                   │
└─────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    2. Caffeine 本地缓存 (L1)                     │
│                                                                 │
│   作用：热点数据缓存，减少 Redis 查询                             │
│   位置：Store Service 进程内                                    │
│   容量：10000 条                                                │
│   过期：逻辑过期策略（30秒 + 异步刷新）                           │
│   存储：热点商品信息                                             │
│                                                                 │
│   逻辑：                                                          │
│     - 命中 → 直接返回                                            │
│     - 未命中 → 查 Redis                                          │
└─────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    3. Redis 分布式缓存 (L2)                       │
│                                                                 │
│   作用：共享缓存，存储库存、商品信息                               │
│   Key 设计：                                                      │
│     - stock:seckill:{skuId}      → 预扣库存（已有）              │
│     - user:bought:{skuId}       → 一人一单标记（已有）            │
│     - product:cache:{skuId}     → 商品信息缓存（新增）             │
│     - stock:rollback:record:{orderNo} → 回滚记录（已有）         │
│                                                                 │
│   逻辑：                                                          │
│     - 命中 → 写入 Caffeine，返回                                   │
│     - 未命中 → 查 MySQL                                           │
└─────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    4. MySQL 数据库 (L3)                           │
│                                                                 │
│   作用：最终数据落地                                               │
│   表：                                                            │
│     - tb_product    → 商品表（id, name, price, stock, category）  │
│                                                                 │
│   回填：查完后异步回填 Redis                                      │
└─────────────────────────────────────────────────────────────────┘
```

### 布隆过滤器设计

#### 1. 参数选择

| 参数 | 值 | 说明 |
|------|-----|------|
| 预期数据量 | 10000 | 预估秒杀商品数量 |
| 误判率 | 1% | 宁可放过，不可错杀 |
| hash 函数数量 | 7 | 最优数量 |
| bit 数组大小 | 95858 bit ≈ 12KB | 自动计算 |

#### 2. Redisson 实现

```java
// 布隆过滤器配置
@Configuration
public class BloomFilterConfig {
    
    @Autowired
    private RedissonClient redisson;
    
    @Bean
    public RBloomFilter<Long> productBloomFilter() {
        RBloomFilter<Long> filter = redisson.getBloomFilter("product:bloom:filter");
        filter.tryInit(10000, 0.01);
        return filter;
    }
}
```

#### 3. 数据初始化

```java
@Service
public class BloomFilterInitService {
    
    @Autowired
    private RBloomFilter<Long> productBloomFilter;
    
    @Autowired
    private ProductMapper productMapper;
    
    @PostConstruct
    public void init() {
        List<Product> products = productMapper.selectList(null);
        for (Product product : products) {
            productBloomFilter.add(product.getId());
        }
        log.info("布隆过滤器初始化完成，共加载 {} 个商品", products.size());
    }
}
```

#### 4. 查询流程

```java
public Product getProductById(Long productId) {
    // 1. 布隆过滤器判断
    if (!bloomFilter.contains(productId)) {
        log.debug("布隆过滤器判断商品不存在，直接返回: productId={}", productId);
        return null;
    }
    
    // 2. 查 Caffeine 本地缓存
    Product cached = caffeineCache.get(productId);
    if (cached != null) {
        return cached;
    }
    
    // 3. 查 Redis
    String key = "product:info:" + productId;
    Product product = redisTemplate.opsForValue().get(key);
    if (product != null) {
        caffeineCache.put(productId, product);
        return product;
    }
    
    // 4. 查 MySQL
    product = productMapper.selectById(productId);
    if (product != null) {
        // 异步回填 Redis 和 Caffeine
        redisTemplate.opsForValue().set(key, product, 1, TimeUnit.HOURS);
        caffeineCache.put(productId, product);
    }
    
    return product;
}
```

---

## Caffeine 本地缓存设计

### 配置

```java
@Configuration
public class CaffeineConfig {
    
    @Bean
    public Cache<Long, Product> productLocalCache() {
        return Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .recordStats()
            .build();
    }
}
```

### 依赖引入

> Spring Boot 3.2.5 已内置 caffeine 版本管理，直接引入即可

```xml
<!-- pom.xml (ecommerce-store) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>

<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

> 注：caffeine 版本由 Spring Boot BOM 统一管理，无需指定版本号

---

## 核心流程

### 商品查询流程

```
用户查询商品 /api/store/product/{id}
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│                  布隆过滤器检查                           │
│                                                          │
│  bloomFilter.contains(productId)                        │
│     │                                                    │
│     ├── 不存在 ──→ 直接返回 null（拦截）                  │
│     │                                                    │
│     └── 可能存在 ──→ 继续往下走                          │
└───────────────────────┬─────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────┐
│                  Caffeine 本地缓存检查                    │
│                                                          │
│  caffeineCache.get(productId)                          │
│     │                                                    │
│     ├── 命中 ──→ 直接返回（性能最优）                      │
│     │                                                    │
│     └── 未命中 ──→ 继续往下走                            │
└───────────────────────┬─────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────┐
│                    Redis 缓存检查                         │
│                                                          │
│  redisTemplate.opsForValue().get(key)                   │
│     │                                                    │
│     ├── 命中 ──→ 回填 Caffeine ──→ 返回                  │
│     │                                                    │
│     └── 未命中 ──→ 继续往下走                            │
└───────────────────────┬─────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────┐
│                    MySQL 数据库查询                       │
│                                                          │
│  productMapper.selectById(productId)                    │
│     │                                                    │
│     ├── 命中 ──→ 回填 Redis ──→ 回填 Caffeine ──→ 返回   │
│     │                                                    │
│     └── 未命中 ──→ 返回 null                            │
└─────────────────────────────────────────────────────────┘
```

---

## 任务清单

### Store Service

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 引入 Caffeine 依赖 | P0 | pom.xml 添加依赖 |
| Caffeine 配置类 | P0 | Cache Bean 配置 |
| 布隆过滤器配置类 | P0 | Redisson RBloomFilter |
| 布隆过滤器初始化 | P0 | 服务启动时加载商品 ID |
| 商品查询多级缓存改造 | P0 | 整合 BF + Caffeine + Redis |
| 新增秒杀商品同步 BF | P1 | 新增商品时同步添加 |
| 缓存命中率监控 | P2 | 记录统计日志 |

### 测试

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 布隆过滤器功能测试 | P0 | 验证不存在商品拦截 |
| 多级缓存链路测试 | P0 | 验证各级缓存命中 |
| 性能压测对比 | P1 | 对比优化前后 QPS |

---

## 验收标准

1. **布隆过滤器生效**：查询不存在的商品 ID，直接返回 null，不查 Redis/MySQL
2. **Caffeine 生效**：热点商品第二次查询，直接从本地缓存返回
3. **多级缓存联动**：三级缓存按顺序查询，未命中逐级回源
4. **功能正常**：原有秒杀下单流程不受影响

---

## 后续扩展

### 分布式架构升级（方案 B）

| 变化点 | 单机版 | 分布式版 |
|--------|--------|---------|
| 布隆过滤器 | Redis 共享 | Redis 共享（不变）|
| Caffeine | L1 本地缓存 | 移除或简化 |
| 数据同步 | 无需同步 | 新增商品需广播 |

> 分布式架构升级时，Caffeine 的价值降低，可考虑移除或改用 Redis 作为一级缓存。