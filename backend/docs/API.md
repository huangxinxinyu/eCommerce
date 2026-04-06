# API 设计

## 1. 设计原则

- RESTful 风格，资源导向
- 网关统一入口，内部服务通过 Dubbo RPC 调用
- 请求/响应格式统一，JSON 传输
- 接口幂等性由业务层保证
- 不涉及缓存、MQ 等中间件细节

---

## 2. 网关路由设计

### 2.1 路由规则

| 路径前缀 | 目标服务 | 说明 |
|----------|----------|------|
| /api/store | Store Service | 秒杀商品接口 |
| /api/order | Order Service | 订单接口 |
| /api/pay | Pay Service | 支付接口 |
| /api/email | Email Service | 通知接口（内部） |

---

## 3. 统一响应格式

### 3.1 成功响应

```json
{
    "code": 200,
    "message": "success",
    "data": { }
}
```

### 3.2 失败响应

```json
{
    "code": 400,
    "message": "库存不足",
    "data": null
}
```

### 3.3 响应码说明

| 响应码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未授权 |
| 404 | 资源不存在 |
| 409 | 业务冲突（如已购买） |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |

---

## 4. Store Service API

**基础路径**：`/api/store`

### 4.1 查询秒杀商品列表

```
GET /api/store/seckill/goods
```

**请求参数**：

| 参数 | 类型 | 位置 | 必填 | 说明 |
|------|------|------|------|------|
| page | int | query | 否 | 页码，默认 1 |
| size | int | query | 否 | 每页数量，默认 10 |

**响应示例**：
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "records": [
            {
                "id": 1,
                "name": "iPhone 15 Pro",
                "price": 7999.00,
                "seckillPrice": 5999.00,
                "stock": 100
            }
        ],
        "total": 10,
        "page": 1,
        "size": 10
    }
}
```

### 4.2 查询秒杀商品详情

```
GET /api/store/seckill/goods/{id}
```

**路径参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 商品ID |

**响应示例**：
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "name": "iPhone 15 Pro",
        "description": "苹果旗舰手机",
        "price": 7999.00,
        "seckillPrice": 5999.00,
        "stock": 100,
    }
}
```

### 4.3 秒杀下单

```
POST /api/store/seckill/order
```

**请求参数**：

| 参数 | 类型 | 位置 | 必填 | 说明 |
|------|------|------|------|------|
| userId | long | body | 是 | 用户ID |
| productId | long | body | 是 | 商品ID |
| quantity | int | body | 否 | 购买数量，默认 1 |

**请求示例**：
```json
{
    "userId": 10001,
    "productId": 1,
    "quantity": 1
}
```

**响应示例**：
```json
{
    "code": 200,
    "message": "下单成功",
    "data": {
        "orderNo": "SK20260406100012345678"
    }
}
```

**业务错误码**：

| 响应码 | message | 说明 |
|--------|---------|------|
| 409 | 商品不存在 | productId 错误 |
| 409 | 秒杀未开始 | 活动未开始 |
| 409 | 秒杀已结束 | 活动已结束 |
| 409 | 已购买过该商品 | 一人一单限制 |
| 409 | 库存不足 | 预扣失败 |

### 4.4 回滚预扣库存

```
POST /api/store/seckill/rollback
```

**请求参数**：

| 参数 | 类型 | 位置 | 必填 | 说明 |
|------|------|------|------|------|
| userId | long | body | 是 | 用户ID |
| productId | long | body | 是 | 商品ID |
| quantity | int | body | 否 | 回滚数量，默认 1 |

**响应示例**：
```json
{
    "code": 200,
    "message": "success",
    "data": true
}
```

---

## 5. Order Service API

**基础路径**：`/api/order`

### 5.1 查询订单

```
GET /api/order/query
```

**请求参数**：

| 参数 | 类型 | 位置 | 必填 | 说明 |
|------|------|------|------|------|
| orderNo | string | query | 否 | 订单号 |
| userId | long | query | 否 | 用户ID |

**响应示例**：
```json
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
        "createTime": "2026-04-06 10:00:00",
        "payTime": null,
        "closeTime": null
    }
}
```

### 5.2 查询订单列表

```
GET /api/order/list
```

**请求参数**：

| 参数 | 类型 | 位置 | 必填 | 说明 |
|------|------|------|------|------|
| userId | long | query | 是 | 用户ID |
| status | int | query | 否 | 订单状态 |
| page | int | query | 否 | 页码，默认 1 |
| size | int | query | 否 | 每页数量，默认 10 |

**响应示例**：
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "records": [
            {
                "orderNo": "SK20260406100012345678",
                "productName": "iPhone 15 Pro",
                "totalAmount": 5999.00,
                "status": 0,
                "createTime": "2026-04-06 10:00:00"
            }
        ],
        "total": 5,
        "page": 1,
        "size": 10
    }
}
```

### 5.3 取消订单

```
POST /api/order/cancel
```

**请求参数**：

| 参数 | 类型 | 位置 | 必填 | 说明 |
|------|------|------|------|------|
| orderNo | string | body | 是 | 订单号 |
| userId | long | body | 是 | 用户ID |

**响应示例**：
```json
{
    "code": 200,
    "message": "success",
    "data": true
}
```

**业务错误码**：

| 响应码 | message | 说明 |
|--------|---------|------|
| 404 | 订单不存在 | 订单号错误 |
| 409 | 订单已支付 | 无法取消已支付订单 |
| 409 | 订单已关闭 | 订单已超时关闭 |

---

## 6. Pay Service API

**基础路径**：`/api/pay`

### 6.1 创建支付

```
POST /api/pay/create
```

**请求参数**：

| 参数 | 类型 | 位置 | 必填 | 说明 |
|------|------|------|------|------|
| orderNo | string | body | 是 | 订单号 |
| userId | long | body | 是 | 用户ID |

**响应示例**：
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "paymentNo": "PAY20260406100087654321",
        "orderNo": "SK20260406100012345678",
        "amount": 5999.00,
        "status": 0
    }
}
```

**业务错误码**：

| 响应码 | message | 说明 |
|--------|---------|------|
| 404 | 订单不存在 | 订单号错误 |
| 409 | 订单已支付 | 重复支付 |
| 409 | 订单已关闭 | 订单已超时 |

### 6.2 模拟支付回调

```
POST /api/pay/callback
```

**请求参数**：

| 参数 | 类型 | 位置 | 必填 | 说明 |
|------|------|------|------|------|
| paymentNo | string | body | 是 | 支付流水号 |
| status | int | body | 是 | 支付状态：1-成功 2-失败 |

**响应示例**：
```json
{
    "code": 200,
    "message": "success",
    "data": true
}
```

### 6.3 查询支付状态

```
GET /api/pay/query
```

**请求参数**：

| 参数 | 类型 | 位置 | 必填 | 说明 |
|------|------|------|------|------|
| paymentNo | string | query | 否 | 支付流水号 |
| orderNo | string | query | 否 | 订单号 |

**响应示例**：
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "paymentNo": "PAY20260406100087654321",
        "orderNo": "SK20260406100012345678",
        "amount": 5999.00,
        "status": 1,
        "payTime": "2026-04-06 10:05:00"
    }
}
```

---

## 7. Email Service API

**说明**：仅供内部 Dubbo RPC 调用，不对外开放

### 7.1 发送订单通知

```
void sendOrderNotification(OrderNotificationRequest request)
```

**请求参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| orderNo | string | 订单号 |
| userId | long | 用户ID |
| type | string | 通知类型：ORDER_CREATED/ORDER_PAID/ORDER_CLOSED |
| content | string | 通知内容 |

### 7.2 发送支付通知

```
void sendPaymentNotification(PaymentNotificationRequest request)
```

**请求参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| paymentNo | string | 支付流水号 |
| userId | long | 用户ID |
| amount | decimal | 支付金额 |
| status | string | 支付状态 |

---

## 8. 内部 Dubbo RPC 接口

### 8.1 Store → Order

```java
@DubboService
public interface OrderDubboService {
    void createOrder(OrderCreateRequest request);
}
```

### 8.2 Order → Store

```java
@DubboService
public interface StockDubboService {
    void rollbackStock(Long productId, Long userId, Integer quantity);
}
```

### 8.3 Pay → Order

```java
@DubboService
public interface PayCallbackService {
    void handlePayCallback(PayCallbackRequest request);
}
```

---

## 9. 版本历史

| 版本 | 日期 | 修改内容 |
|------|------|----------|
| 1.0 | 2026-04-06 | 初始 API 设计 |

---

> 本文档仅包含接口协议设计，与数据库、缓存、MQ 等实现细节解耦。
