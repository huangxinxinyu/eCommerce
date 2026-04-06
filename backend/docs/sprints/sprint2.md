# Sprint 2：交易闭环

## 目标

**完成从下单到支付/关单的完整交易闭环**

- Order Service 消费 MQ 完成订单创建落库
- 模拟支付回调流程（不做第三方对接）
- 超时订单自动关闭
- 库存恢复（支付失败/关单时回滚）且保证幂等

---

## 核心流程

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         交易闭环完整流程                                   │
└─────────────────────────────────────────────────────────────────────────┘

【支付成功路径】
用户发起支付
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│                   Pay Service                            │
│                                                          │
│  1. 接收支付请求（模拟）                                  │
│  2. 生成支付流水                                          │
│  3. 模拟银行处理（100%成功）                              │
│  4. 发送支付成功 MQ 到 Order Service                     │
│  5. 发送通知 MQ 到 Email Service（模拟）                  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ MQ: pay-success
                        ▼
┌─────────────────────────────────────────────────────────┐
│                   Order Service                            │
│                                                          │
│  1. 消费支付成功消息                                      │
│  2. 更新订单状态 → 已支付                                │
│  3. 发送邮件/短信通知（MQ）                              │
└─────────────────────────────────────────────────────────┘

【超时关单路径】
延迟消息触发（15分钟后）
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│                   Order Service                            │
│                                                          │
│  1. 扫描待支付超时订单                                    │
│  2. 分布式锁防止并发关单                                 │
│  3. 更新订单状态 → 已关闭                                │
│  4. 发送库存恢复消息到 Store Service                     │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ MQ: stock-rollback
                        ▼
┌─────────────────────────────────────────────────────────┐
│                   Store Service                            │
│                                                          │
│  1. 消费库存恢复消息                                      │
│  2. 幂等校验（防止重复恢复）                             │
│  3. Lua 脚本原子恢复库存                                 │
│  4. 清除一人一单标记                                     │
│  5. 返回恢复结果                                          │
└─────────────────────────────────────────────────────────┘
                        │
                        │ Dubbo: confirmStockRollback
                        ▼
┌─────────────────────────────────────────────────────────┐
│                   Order Service                            │
│                                                          │
│  6. Dubbo 回调确认库存恢复成功                           │
│  7. 若确认失败，抛出异常触发 MQ 重试                     │
└─────────────────────────────────────────────────────────┘

【用户取消路径】
用户主动取消
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│                   Order Service                            │
│                                                          │
│  1. 校验订单状态（仅待支付可取消）                       │
│  2. 分布式锁防止并发操作                                 │
│  3. 更新订单状态 → 已取消                                │
│  4. 发送库存恢复消息                                     │
└─────────────────────────────────────────────────────────┘
```

---

## 技术要点

### 1. 订单状态流转

```
    ┌──────────┐
    │ 待支付   │ ←── 用户下单
    └─────┬────┘
          │
    ┌─────┴─────┐
    │           │
    ▼           ▼
┌────────┐  ┌────────┐
│ 已支付 │  │ 已关闭 │
└────────┘  └────────┘
            ↑
    ┌───────┴───────┐
    │               │
    ▼               ▼
超时关单       用户取消
```

| 状态值 | 状态名 | 说明 |
|--------|--------|------|
| 0 | PENDING | 待支付 |
| 1 | PAID | 已支付 |
| 2 | CLOSED | 已关闭（超时） |
| 3 | CANCELLED | 已取消（用户主动） |

### 2. 模拟支付流程

```java
@Data
public class PayRequest implements Serializable {
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
}

@Data
public class PayCallbackMessage implements Serializable {
    private String orderNo;
    private String payNo;           // 支付流水号
    private Integer payStatus;      // 1:成功 2:失败
    private BigDecimal paidAmount;
    private Long payTime;
}
```

- 支付请求直接返回成功，模拟银行回调
- 生成唯一支付流水号（payNo）
- 支付金额与订单金额校验

### 3. 超时关单实现

```java
@RocketMQMessageListener(
    topic = "order-delay",
    consumerGroup = "order-close-consumer",
    tag = "delay"
)
public class OrderDelayConsumer {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private RedissonClient redisson;
    
    public void onMessage(OrderDelayMessage msg) {
        String lockKey = "order:close:lock:" + msg.getOrderNo();
        RLock lock = redisson.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待 3 秒，锁自动过期 10 秒
            if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                orderService.closeOrder(msg.getOrderNo());
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

- 使用延迟消息触发关单（消息延迟 15 分钟）
- 分布式锁保证并发安全
- 关单前校验订单状态，防止状态冲突

### 4. 库存恢复幂等

```lua
-- stock-rollback.lua
-- 库存恢复 + 幂等校验，原子操作

-- KEYS[1]: 库存 Key (stock:seckill:{skuId})
-- KEYS[2]: 用户购买记录 Key (user:bought:{skuId})
-- KEYS[3]: 恢复记录 Key (stock:rollback:record:{orderNo})
-- ARGV[1]: 恢复数量
-- ARGV[2]: orderNo

-- 1. 幂等校验：检查是否已处理过
local alreadyRollback = redis.call('EXISTS', KEYS[3])
if alreadyRollback == 1 then
    return 0  -- 已恢复过，跳过
end

-- 2. 原子恢复库存
redis.call('INCRBY', KEYS[1], ARGV[1])

-- 3. 清除一人一单标记
redis.call('SREM', KEYS[2], ARGV[2])

-- 4. 标记该订单已恢复（防止重复）
redis.call('SET', KEYS[3], '1', 'EX', 86400)

return 1  -- 成功
```

- 幂等 key 存储在 Redis，过期时间 24 小时
- 恢复前先校验是否已处理
- 库存恢复和一人一单清除在同一事务中

### 5. 消息幂等处理

```java
public interface IdempotentService {
    
    boolean tryAcquire(String bizType, String bizId, long expireSeconds);
    
    void release(String bizType, String bizId);
}

@Service
public class RedisIdempotentService implements IdempotentService {
    
    @Autowired
    private StringRedisTemplate template;
    
    @Override
    public boolean tryAcquire(String bizType, String bizId, long expireSeconds) {
        String key = String.format("idempotent:%s:%s", bizType, bizId);
        Boolean success = template.opsForValue().setIfAbsent(key, "1", expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }
}
```

- 基于 Redis SETNX 实现幂等
- 不同业务类型使用不同 key 前缀
- 过期时间防止无限占用

### 6. Dubbo 回调确认库存

```java
// Store Service 提供 Dubbo 接口
@DubboService
public interface StockDubboService {
    
    /**
     * 确认库存回滚结果
     * @param orderNo 订单号
     * @param productId 商品ID
     * @param userId 用户ID
     * @param quantity 恢复数量
     * @return true 确认成功，false 确认失败
     */
    boolean confirmStockRollback(String orderNo, Long productId, Long userId, Integer quantity);
}

// Store Service 实现
@DubboService
public class StockDubboServiceImpl implements StockDubboService {
    
    @Autowired
    private RedissonClient redisson;
    
    @Override
    public boolean confirmStockRollback(String orderNo, Long productId, Long userId, Integer quantity) {
        String rollbackKey = "stock:rollback:record:" + orderNo;
        String boughtKey = "user:bought:" + productId;
        
        RLock lock = redisson.getLock("lock:confirm:" + orderNo);
        try {
            lock.lock(5, TimeUnit.SECONDS);
            
            // 1. 检查幂等标记是否存在
            Boolean exists = redisTemplate.hasKey(rollbackKey);
            if (!Boolean.TRUE.equals(exists)) {
                log.warn("库存恢复幂等标记不存在, orderNo={}", orderNo);
                return false;
            }
            
            // 2. 检查一人一单标记是否已清除
            Boolean hasBought = redisTemplate.opsForSet().isMember(boughtKey, userId.toString());
            if (Boolean.TRUE.equals(hasBought)) {
                log.warn("一人一单标记未清除, orderNo={}, userId={}", orderNo, userId);
                return false;
            }
            
            // 3. 检查库存是否已恢复（通过对比 Redis 和 DB）
            String stockKey = "stock:seckill:" + productId;
            String stockStr = redisTemplate.opsForValue().get(stockKey);
            if (stockStr == null) {
                log.warn("库存 Key 不存在, orderNo={}", orderNo);
                return false;
            }
            
            return true;
        } finally {
            lock.unlock();
        }
    }
}

// Order Service 调用确认
@Service
public class OrderCloseService {
    
    @DubboReference
    private StockDubboService stockDubboService;
    
    public void closeOrder(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        
        // 发送 MQ 库存恢复消息
        StockRollbackMessage message = new StockRollbackMessage();
        message.setOrderNo(orderNo);
        message.setProductId(order.getProductId());
        message.setUserId(order.getUserId());
        message.setQuantity(order.getQuantity());
        
        rocketMQTemplate.asyncSend("stock-rollback:rollback", message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                // MQ 发送成功后，等待 Store 处理，然后 Dubbo 确认
                confirmRollbackWithRetry(message);
            }
            
            @Override
            public void onException(Throwable e) {
                log.error("发送库存恢复消息失败", e);
                throw new RuntimeException("库存恢复消息发送失败");
            }
        });
    }
    
    private void confirmRollbackWithRetry(StockRollbackMessage message) {
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                boolean confirmed = stockDubboService.confirmStockRollback(
                    message.getOrderNo(),
                    message.getProductId(),
                    message.getUserId(),
                    message.getQuantity()
                );
                
                if (confirmed) {
                    log.info("库存恢复确认成功, orderNo={}", message.getOrderNo());
                    return;
                }
                
                log.warn("库存恢复确认失败，尝试重试, orderNo={}, retryCount={}", 
                    message.getOrderNo(), retryCount + 1);
                
            } catch (Exception e) {
                log.error("Dubbo 调用异常, orderNo={}, retryCount={}", 
                    message.getOrderNo(), retryCount + 1, e);
            }
            
            retryCount++;
            if (retryCount < maxRetries) {
                try {
                    Thread.sleep(1000 * retryCount); // 指数退避
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // 重试耗尽，抛出异常，触发 MQ 重新投递
        throw new RuntimeException("库存恢复确认失败，已重试 " + maxRetries + " 次");
    }
}
```

- 使用 Dubbo 同步调用确认库存恢复结果
- 包含 3 次重试机制，指数退避策略
- 确认失败时抛出异常，MQ 消费失败触发重试
- 幂等标记在 Lua 脚本中自动设置

---

## 任务清单

### Pay Service

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 基础框架搭建 | P0 | pom、配置、启动类 |
| 支付请求接口 | P0 | 接收前端支付请求 |
| 模拟银行处理 | P0 | 直接返回成功，生成支付流水 |
| 支付回调 MQ | P0 | 发送支付成功消息到 Order |
| 支付流水表 | P0 | 记录支付记录 |
| 支付查询接口 | P1 | 查询支付状态 |

### Order Service

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 支付成功消费 | P0 | 消费 MQ，更新订单状态 |
| 超时关单逻辑 | P0 | 扫描并关闭超时订单 |
| 取消订单接口 | P0 | 用户主动取消 |
| 库存恢复消息 | P0 | 发送库存恢复 MQ |
| 分布式锁集成 | P0 | Redisson 实现锁 |
| 订单状态校验 | P0 | 状态机校验 |
| 幂等处理 | P0 | MQ 消费幂等 |
| Dubbo 引用配置 | P0 | 引用 Store Service 确认接口 |
| 库存确认重试 | P0 | Dubbo 回调 + 指数退避重试 |

### Store Service

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 库存恢复消费 | P0 | 消费 MQ，执行恢复 |
| 恢复幂等 Lua | P0 | 防止重复恢复 |
| 一人一单清除 | P0 | 恢复时清除标记 |
| Dubbo 服务暴露 | P0 | 提供库存确认 Dubbo 接口 |

### Email Service

| 任务 | 优先级 | 说明 |
|------|--------|------|
| MQ 消费配置 | P0 | 监听通知消息 |
| 支付成功通知 | P0 | Log 模拟发送通知 |
| 订单关闭通知 | P0 | Log 模拟发送通知 |

### 公共

| 任务 | 优先级 | 说明 |
|------|--------|------|
| MQ Topic 配置 | P0 | pay-success、stock-rollback、notify |
| MQ 消费组配置 | P0 | 各服务消费组 |
| 分布式锁封装 | P0 | Redisson 集成 |
| 幂等组件 | P0 | 通用幂等工具 |

---

## API 接口

### Pay Service

```
POST /api/pay/create
Request:
{
    "orderNo": "ORDER202401011200001",
    "userId": 10001,
    "amount": 99.00,
}
Response:
{
    "code": 200,
    "message": "支付创建成功",
    "data": {
        "payNo": "PAY202401011200001",
        "orderNo": "ORDER202401011200001",
        "amount": 99.00,
        "payStatus": 0
    }
}

POST /api/pay/callback
Request:
{
    "payNo": "PAY202401011200001",
    "status": 1,
    "paidAmount": 99.00,
    "payTime": 1704067200000
}
Response:
{
    "code": 200,
    "message": "回调处理成功"
}

GET /api/pay/query/{payNo}
Response:
{
    "code": 200,
    "data": {
        "payNo": "PAY202401011200001",
        "orderNo": "ORDER202401011200001",
        "amount": 99.00,
        "status": 1,
        "payTime": 1704067200000
    }
}
```

### Order Service

```
POST /api/order/cancel/{orderNo}

Response:
{
    "code": 200,
    "message": "订单取消成功"
}

GET /api/order/{orderNo}
Response:
{
    "code": 200,
    "data": {
        "orderNo": "ORDER202401011200001",
        "userId": 10001,
        "productId": 1,
        "productName": "iPhone 15",
        "quantity": 1,
        "price": 99.00,
        "status": 1,
        "payNo": "PAY202401011200001",
        "createTime": "2024-01-01 12:00:00",
        "payTime": "2024-01-01 12:05:00"
    }
}

GET /api/order/list/{userId}
Response:
{
    "code": 200,
    "data": [
        {
            "orderNo": "ORDER202401011200001",
            "status": 1,
            "totalAmount": 99.00
        }
    ]
}
```

### Email Service（仅 Log 输出）

```
消费 MQ 消息，模拟发送通知：

支付成功：
[EMAIL] 发送邮件到 user@example.com
        主题: 您的订单已支付成功
        内容: 订单号 ORDER202401011200001，金额 99.00 元

关单通知：
[EMAIL] 发送邮件到 user@example.com
        主题: 您的订单已关闭
        内容: 订单号 ORDER202401011200001 因超时未支付已关闭
```

---

## MQ 消息列表

| Topic | Tag | 发送方 | 消费方 | 说明 |
|-------|-----|--------|--------|------|
| pay-success | pay | Pay Service | Order Service | 支付成功通知 |
| stock-rollback | rollback | Order Service | Store Service | 库存恢复 |
| order-notify | notify | Order Service | Email Service | 通知消息 |
| order-delay | delay | Order Service | Order Service | 延迟关单消息 |

## Dubbo 接口列表

| 接口 | 提供方 | 消费方 | 说明 |
|------|--------|--------|------|
| StockDubboService.confirmStockRollback | Store Service | Order Service | 确认库存回滚结果 |

---

## 验收标准

- [ ] 支付请求能够创建支付流水并返回
- [ ] 支付成功后订单状态正确更新为已支付
- [ ] 超时 15 分钟的订单能够自动关闭
- [ ] 订单取消能够成功且库存恢复
- [ ] 库存恢复保证幂等，重复消费不重复恢复
- [ ] 所有 MQ 消费保证幂等
- [ ] 分布式锁正确保护并发操作
- [ ] 通知消息正确发送（Log 验证）
- [ ] Dubbo 回调确认库存恢复结果正确
- [ ] 库存确认失败时触发重试机制（3次，指数退避）
