# 数据库设计

## 1. 设计原则

- 按业务领域拆分数据库，实现物理隔离
- 单表字段精简，避免大字段频繁更新
- 索引设计遵循：查询条件、排序字段、唯一约束优先
- 订单号采用分布式 ID 生成，保证全局唯一

---

## 2. 数据库划分

```
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│ ecommerce_store │   │ ecommerce_order  │   │  ecommerce_pay   │   │ecommerce_common │
│    (商品服务)    │   │    (订单服务)    │   │    (支付服务)    │   │    (公共服务)    │
└─────────────────┘   └─────────────────┘   └─────────────────┘   └─────────────────┘
```

| 数据库 | 服务 | 用途 |
|--------|------|------|
| ecommerce_store | Store Service | 秒杀商品数据 |
| ecommerce_order | Order Service | 订单数据 |
| ecommerce_pay | Pay Service | 支付流水数据 |
| ecommerce_common | 公共服务 | 用户等通用数据 |

---

## 3. Store 数据库

### 3.1 商品表 (tb_product)

**用途**：存储秒杀商品基础信息

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 商品ID |
| name | VARCHAR(200) | NOT NULL | 商品名称 |
| price | DECIMAL(10,2) | NOT NULL | 商品价格 |
| stock | INT | UNSIGNED, DEFAULT 0 | 库存数量 |
| description | TEXT | | 商品描述 |
| category | VARCHAR(50) | | 商品分类 |
| version | INT | UNSIGNED, DEFAULT 0 | 乐观锁版本号 |
| create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引设计**：

| 索引名 | 类型 | 字段 | 用途 |
|--------|------|------|------|
| idx_category | NORMAL | category | 按分类查询商品 |

**说明**：
- `stock` 为最终库存，支付成功后扣减
- `version` 用于乐观锁，防止并发更新
- 大字段 `description` 单独存储，避免影响主表查询

---

## 4. Order 数据库

### 4.1 订单表 (tb_order)

**用途**：存储秒杀订单核心数据

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 订单ID |
| order_no | VARCHAR(64) | NOT NULL, UNIQUE | 订单号 |
| user_id | BIGINT | NOT NULL | 用户ID |
| product_id | BIGINT | NOT NULL | 商品ID |
| product_name | VARCHAR(200) | NOT NULL | 商品名称 |
| price | DECIMAL(10,2) | NOT NULL | 下单价格 |
| quantity | INT | NOT NULL, DEFAULT 1 | 购买数量 |
| total_amount | DECIMAL(10,2) | NOT NULL | 订单总金额 |
| status | TINYINT | NOT NULL, DEFAULT 0 | 订单状态 |
| create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| pay_time | DATETIME | | 支付时间 |
| close_time | DATETIME | | 关闭时间 |
| update_time | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**订单状态**：

| 状态值 | 状态名 | 说明 |
|--------|--------|------|
| 0 | PENDING | 待支付 |
| 1 | PAID | 已支付 |
| 2 | CLOSED | 已关闭（超时） |
| 3 | CANCELLED | 已取消（用户主动） |

**索引设计**：

| 索引名 | 类型 | 字段 | 用途 |
|--------|------|------|------|
| idx_user_id | NORMAL | user_id | 按用户查询订单 |
| idx_order_no | UNIQUE | order_no | 订单号唯一查询 |
| idx_status | NORMAL | status | 按状态筛选订单 |
| idx_create_time | NORMAL | create_time | 按时间排序查询 |

**说明**：
- `order_no` 全局唯一，用于 MQ 消息关联
- `total_amount = price × quantity`
- `close_time` 用于记录超时关闭时间
- 索引 `idx_status + idx_create_time` 可优化待处理订单扫描

---

## 5. Pay 数据库

### 5.1 支付流水表 (tb_payment)

**用途**：存储支付流水记录

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 支付ID |
| payment_no | VARCHAR(64) | NOT NULL, UNIQUE | 支付流水号 |
| order_no | VARCHAR(64) | NOT NULL | 关联订单号 |
| user_id | BIGINT | NOT NULL | 用户ID |
| amount | DECIMAL(10,2) | NOT NULL | 支付金额 |
| status | TINYINT | NOT NULL, DEFAULT 0 | 支付状态 |
| create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| pay_time | DATETIME | | 支付时间 |
| update_time | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**支付状态**：

| 状态值 | 状态名 | 说明 |
|--------|--------|------|
| 0 | PENDING | 待支付 |
| 1 | SUCCESS | 支付成功 |
| 2 | FAILED | 支付失败 |
| 3 | REFUNDED | 已退款 |

**索引设计**：

| 索引名 | 类型 | 字段 | 用途 |
|--------|------|------|------|
| idx_payment_no | UNIQUE | payment_no | 支付流水号唯一查询 |
| idx_order_no | NORMAL | order_no | 关联订单查询 |
| idx_user_id | NORMAL | user_id | 按用户查询支付记录 |

---

## 6. Common 数据库

### 6.1 用户表 (tb_user)

**用途**：存储用户基础信息（简化版）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 用户ID |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 用户名 |
| phone | VARCHAR(20) | | 手机号 |
| email | VARCHAR(100) | | 邮箱 |
| create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引设计**：

| 索引名 | 类型 | 字段 | 用途 |
|--------|------|------|------|
| idx_username | UNIQUE | username | 用户名唯一查询 |

---

## 7. 命名规范

### 7.1 表命名

```
{tb_}{业务标识}_{实体名}
```

示例：
- `tb_product` - 商品表
- `tb_order` - 订单表
- `tb_payment` - 支付表
- `tb_user` - 用户表

### 7.2 字段命名

| 类型 | 规范 | 示例 |
|------|------|------|
| 主键 | `{实体}_id` | `product_id`、`order_id` |
| 外键 | `{实体}_id` | `user_id`、`product_id` |
| 金额 | `DECIMAL(10,2)` | `price`、`amount`、`total_amount` |
| 数量 | `INT` | `stock`、`quantity` |
| 状态 | `TINYINT` | `status` |
| 时间 | `DATETIME` | `create_time`、`update_time` |

### 7.3 索引命名

```
idx_{表名}_{字段名}
```

示例：
- `idx_product_category`
- `idx_order_user_id`

---

## 8. 版本历史

| 版本 | 日期 | 修改内容 |
|------|------|----------|
| 1.0 | 2026-04-06 | 初始数据库设计 |

---

> 本文档仅包含数据库表结构设计，与缓存、MQ 等中间件设计解耦。
