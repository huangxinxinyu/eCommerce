# Sprint 1：库存预扣与订单创建

## 目标

**打通从扣库存到生成订单的完整链路**

- Store Service 实现库存预扣（Lua 脚本）
- Order Service 异步消费 MQ 创建订单
- 在数据库创建待支付状态订单

---

## 核心流程

```
用户请求
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│                   Store Service                          │
│                                                          │
│  1. 校验秒杀活动状态                                      │
│  2. 检查一人一单（Redis Set）                            │
│  3. Lua 脚本原子预扣库存                                 │
│  4. 发送 MQ 消息到 Order Service                        │
│  5. 返回下单成功                                         │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ MQ: seckill-order
                        ▼
┌─────────────────────────────────────────────────────────┐
│                   Order Service                          │
│                                                          │
│  1. 消费 MQ 消息                                        │
│  2. 查询商品信息                                        │
│  3. 创建订单（待支付状态）                              │
│  4. 发送延迟关单消息（15分钟后）                       │
└─────────────────────────────────────────────────────────┘
```

---

## 技术要点

### 1. Redis Lua 脚本

```lua
-- seckill.lua
-- 库存预扣 + 一人一单，原子操作

-- KEYS[1]: 库存 Key (stock:seckill:{skuId})
-- KEYS[2]: 用户购买记录 Key (user:bought:{skuId})
-- ARGV[1]: 购买数量
-- ARGV[2]: userId

-- 1. 检查用户是否已购买
local alreadyBought = redis.call('SISMEMBER', KEYS[2], ARGV[2])
if alreadyBought == 1 then
    return -1  -- 已购买
end

-- 2. 检查库存
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock < tonumber(ARGV[1]) then
    return 0  -- 库存不足
end

-- 3. 原子扣减库存
redis.call('DECRBY', KEYS[1], ARGV[1])

-- 4. 标记用户已购买
redis.call('SADD', KEYS[2], ARGV[2])

return 1  -- 成功
```

### 2. MQ 消息体

```java
@Data
public class OrderCreateMessage implements Serializable {
    private String orderNo;
    private Long userId;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private Long timestamp;
}
```

### 3. 订单状态

| 状态值 | 状态名 | 说明 |
|--------|--------|------|
| 0 | PENDING | 待支付 |
| 1 | PAID | 已支付 |
| 2 | CLOSED | 已关闭 |
| 3 | CANCELLED | 已取消 |

---

## 任务清单

### Store Service

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 基础框架搭建 | P0 | pom、配置、启动类 |
| 商品 CRUD | P0 | 商品查询、详情接口 |
| Redis 连接配置 | P0 | 连接池、序列化配置 |
| Lua 脚本加载 | P0 | 预扣库存脚本 |
| 一人一单校验 | P0 | Redis Set 操作 |
| 秒杀下单接口 | P0 | 预扣 + 发 MQ 消息 |
| 库存回滚接口 | P1 | 支付失败/关单时回滚 |

### Order Service

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 基础框架搭建 | P0 | pom、配置、启动类 |
| 订单 Mapper | P0 | MyBatis-Plus CRUD |
| MQ 消费者配置 | P0 | RocketMQ 监听配置 |
| 订单创建逻辑 | P0 | 消费消息，落库 |
| 延迟关单消息 | P1 | 发送延迟消息 |
| 订单查询接口 | P0 | HTTP 接口 |

### Gateway Service

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 基础框架搭建 | P0 | pom、配置、启动类 |
| Nacos 配置中心 | P0 | 注册发现、配置管理 |
| 路由配置 | P0 | 路由到各微服务 |
| 统一响应包装 | P0 | 响应格式标准化 |
| CORS 跨域配置 | P0 | 前后端分离支持 |
| 日志记录 | P1 | 请求日志 |

### User Service

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 基础框架搭建 | P0 | pom、配置、启动类 |
| 用户 Mapper | P0 | MyBatis-Plus CRUD |
| 用户注册 | P0 | 注册接口 |
| 用户登录 | P0 | 登录接口，返回 Token |
| 用户查询 | P0 | 查询用户信息 |
| 用户更新 | P1 | 更新用户信息 |

### Store Service

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 基础框架搭建 | P0 | pom、配置、启动类 |
| 商品 CRUD | P0 | 商品查询、详情接口 |
| Redis 连接配置 | P0 | 连接池、序列化配置 |
| Lua 脚本加载 | P0 | 预扣库存脚本 |
| 一人一单校验 | P0 | Redis Set 操作 |
| 秒杀下单接口 | P0 | 预扣 + 发 MQ 消息 |
| 库存回滚接口 | P1 | 支付失败/关单时回滚 |

### Order Service

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 基础框架搭建 | P0 | pom、配置、启动类 |
| 订单 Mapper | P0 | MyBatis-Plus CRUD |
| MQ 消费者配置 | P0 | RocketMQ 监听配置 |
| 订单创建逻辑 | P0 | 消费消息，落库 |
| 延迟关单消息 | P1 | 发送延迟消息 |
| 订单查询接口 | P0 | HTTP 接口 |

### 公共

| 任务 | 优先级 | 说明 |
|------|--------|------|
| Docker Compose | P0 | MySQL、Redis、Nacos、RocketMQ |
| 数据库表 | P0 | tb_user、tb_product、tb_order |
| MQ Topic 配置 | P0 | seckill-order |
| 统一实体类 | P1 | 公共 DTO、VO 定义 |
| 工具类 | P1 | 序列号生成、日期工具 |

---

## API 接口

### Store Service

```
POST /api/store/seckill/order
Request:
{
    "userId": 10001,
    "productId": 1,
    "quantity": 1
}
Response:
{
    "code": 200,
    "message": "下单成功",
    "data": {
        "orderNo": "SK20260406100012345678"
    }
}

POST /api/store/seckill/rollback
Request:
{
    "userId": 10001,
    "productId": 1,
    "quantity": 1
}
Response:
{
    "code": 200,
    "message": "success",
    "data": true
}
```

### Order Service

```
GET /api/order/query?orderNo=xxx
Response:
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "orderNo": "SK20260406100012345678",
        "userId": 10001,
        "productId": 1,
        "productName": "iPhone 15 Pro",
        "price": 5999.00,
        "quantity": 1,
        "totalAmount": 5999.00,
        "status": 0,
        "createTime": "2026-04-06 10:00:00"
    }
}
```

---

## API 接口

### Gateway（统一入口）

```
所有请求通过 Gateway 路由：
  /api/user/*    → User Service
  /api/store/*   → Store Service
  /api/order/*   → Order Service
```

### User Service

```
POST /api/user/register
Request:
{
    "username": "zhangsan",
    "email": "zhangsan@example.com",
    "phone": "13800138000"
}
Response:
{
    "code": 200,
    "message": "注册成功",
    "data": {
        "userId": 10001
    }
}

POST /api/user/login
Request:
{
    "username": "zhangsan"
}
Response:
{
    "code": 200,
    "message": "登录成功",
    "data": {
        "userId": 10001,
        "username": "zhangsan"
    }
}
说明：高并发场景简化设计，无需密码验证

GET /api/user/info?userId=10001
Response:
{
    "code": 200,
    "message": "success",
    "data": {
        "userId": 10001,
        "username": "zhangsan",
        "email": "zhangsan@example.com",
        "phone": "13800138000",
        "createTime": "2026-04-06 10:00:00"
    }
}

PUT /api/user/update
Request:
{
    "userId": 10001,
    "email": "new@example.com",
    "phone": "13900139000"
}
Response:
{
    "code": 200,
    "message": "更新成功",
    "data": true
}
```

### Store Service

```
GET /api/store/goods
Response:
{
    "code": 200,
    "message": "success",
    "data": [
        {
            "id": 1,
            "name": "iPhone 15 Pro",
            "price": 5999.00,
            "stock": 100,
            "description": "苹果旗舰手机"
        }
    ]
}

GET /api/store/goods/{id}
Response:
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "name": "iPhone 15 Pro",
        "price": 5999.00,
        "stock": 100,
        "description": "苹果旗舰手机",
        "imageUrl": "https://example.com/iphone15.jpg"
    }
}

```
1. 预扣库存成功
2. 发送 MQ 消息
3. Order Service 消费消息
4. 创建待支付订单
5. 验证：订单表中存在 PENDING 状态订单
```

### 场景 2：库存不足

```
1. 库存 = 0
2. 预扣失败
3. 返回"库存不足"
4. 验证：未发送 MQ 消息
```

### 场景 3：重复购买

```
1. 用户已购买过
2. 预扣失败
3. 返回"已购买过该商品"
4. 验证：订单表已有该用户订单
```

### 场景 4：库存回滚

```
1. 模拟关单场景
2. 调用回滚接口
3. Redis 库存恢复
4. 用户购买标记移除
5. 验证：库存已恢复
```

---

## 里程碑

| 里程碑 | 验收标准 |
|--------|----------|
| M0 | Nacos + Gateway 启动成功，请求可路由 |
| M1 | User Service 启动成功，用户注册/登录可用 |
| M2 | Store Service 启动成功，Redis Lua 脚本预扣库存成功 |
| M3 | Order Service 消费消息创建订单 |
| M4 | 完整链路测试通过（注册 → 登录 → 秒杀 → 创建订单） |

---

## 预计产出

- Gateway Service 模块（可运行）
- User Service 模块（可运行）
- Store Service 模块（可运行）
- Order Service 模块（可运行）
- Docker Compose 环境配置（Nacos + MySQL + Redis + RocketMQ）
- 完整链路可测试

---

## 暂不实现

- 支付流程
- 延迟关单消费
- 多级缓存
- 限流
- 熔断降级

---

## 版本历史

| 版本 | 日期 | 修改内容 |
|------|------|----------|
| 1.0 | 2026-04-06 | 初始 Sprint 1 计划 |
| 1.1 | 2026-04-06 | 补充 Gateway + User Service 基础架构 |
