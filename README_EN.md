# High-Performance Flash Sale System

## Project Overview

A high-performance flash sale (seckill) e-commerce platform built with microservices architecture. The system handles massive concurrent requests for limited-inventory flash sales through multi-level caching, atomic Redis operations, and asynchronous messaging.

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Gateway   │────▶│  User Svc   │     │   Pay Svc   │
└─────────────┘     └─────────────┘     └─────────────┘
                          │                    │
                          ▼                    │
                    ┌─────────────┐            │
                    │  Store Svc  │◀───────────┘
                    │  (SECCORE)  │
                    └─────────────┘
                          │
         ┌────────────────┼────────────────┐
         ▼                ▼                ▼
   ┌──────────┐    ┌────────────┐    ┌──────────┐
   │  Redis   │   │ RocketMQ   │    │  MySQL   │
   └──────────┘   └────────────┘    └──────────┘
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 17 |
| Framework | Spring Boot 3.2, Spring Cloud 2023 |
| RPC | Apache Dubbo 3.2 |
| MQ | RocketMQ 4.9 |
| Database | MySQL 8 + MyBatis Plus |
| Cache | Redis + Redisson + Caffeine |
| API Gateway | Spring Cloud Gateway |

## Core Solutions

### 1. Multi-Level Cache Architecture

```
Request → BloomFilter → Caffeine(L1) → Redis(L2) → MySQL(L3)
```

- **BloomFilter**: Blocks non-existent product IDs, prevents cache penetration
- **Caffeine**: In-process cache for hot product data (10K entry limit, 30s TTL)
- **Redis**: Distributed cache for stock counts and user purchase records

### 2. Atomic Stock Operations (Redis Lua)

seckill.lua: Atomic stock pre-block + one-per-person check

```lua
-- 1. Check if user already purchased
-- 2. Check stock availability
-- 3. Atomically: decrement stock + mark user purchased
-- Returns: 1=success, 0=no stock, -1=already bought
```

- Lua scripts ensure atomic stock operations, preventing overselling
- Enforces one-per-person purchase limit

### 3. Asynchronous Order Processing

```
User Request → MQ → Order Service → Create Order → Payment Wait
```

- Immediate response with order number, better UX
- Async order creation via RocketMQ streams
- Decouples flash sale spike from order processing

### 4. TCC-like Stock Management

- **Try**: Pre-block stock in Redis (Lua atomic operation)
- **Confirm**: Deduct actual stock in MySQL after payment
- **Cancel**: Restore Redis stock via MQ on timeout/cancel

### 5. Distributed Locking (Redisson)

- Order payment updates: prevents double payment
- Order timeout close: ensures only one closer
- Stock rollback confirmation: prevents duplicate rollback

## Key Modules

| Module | Responsibility |
|--------|----------------|
| `ecommerce-gateway` | Rate limiting, request routing |
| `ecommerce-user` | User registration, authentication |
| `ecommerce-store` | Product catalog, seckill core logic, stock management |
| `ecommerce-order` | Order lifecycle, timeout close, cancellation |
| `ecommerce-pay` | Payment creation, callback processing |
| `ecommerce-email` | Async notification (simulated) |

## Message Flows

| Topic | Purpose |
|-------|---------|
| `seckill-order` | Async order creation |
| `order-delay` | 15-min timeout close scheduling |
| `pay-success` | Payment notification to order service |
| `stock-rollback` | Stock restoration on cancel/timeout |
| `pay-confirmed` | Actual stock deduction after payment confirmed |
