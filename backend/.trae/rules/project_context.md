# 1. 项目背景：
本系统是一个基于 Spring Cloud 的纯后端分布式架构，旨在模拟双 11 级别的瞬时大流量秒杀场景。系统通过 Nginx 与 Gateway 构建多层防护，利用 Redis + Lua 实现极速库存预扣，并配合 RocketMQ 异步解耦，确保在高并发下系统依然能保持最终一致性。

# 2. 核心技术栈 (Technology Stack)
接入层： Nginx 负责 L7 负载均衡，将流量均匀分发给 Gateway 节点。
控制层： Gateway 负责流量清洗（限流）与安全检查。
通信层： 内部微服务通过 Dubbo RPC 同步调用，关键路径采用 RocketMQ 异步解耦。
执行层： Redis + Lua 承载瞬时峰值，MySQL 保证最终数据落地。

维度	技术选型	说明
基础框架	Spring Boot 3.x + Spring Cloud Alibaba	微服务治理核心
RPC 框架	Apache Dubbo 3.x	取代 OpenFeign，提供高性能、低延迟的微服务调用
注册中心	Nacos	同时作为 Dubbo 服务发现与 Spring Cloud 配置中心
分流与均衡	Nginx + Gateway	Nginx 做最前端分流，Gateway 做业务侧治理
消息中间件	RocketMQ	事务消息、削峰填谷、延迟消息关单
持久层	MySQL + MyBatis-Plus	最终数据落地
本地治具	Docker Compose + Jmeter	用于本地分布式环境搭建与高并发压测

---
# 3. 系统架构设计 (Service Architecture)
3.1 服务拆分
1. Gateway Service (入口网关):
  - 职责：路由转发、滑动窗口限流、CORS 处理。
2. Store Service (秒杀核心):
  - 职责：热点商品信息缓存、Lua 脚本库存预扣、一人一单校验。
3. Order Service (订单):
  - 职责：异步创建订单、自动关单逻辑 (MQ 延迟消息)、状态同步。
4. Pay Service （银行服务）
  - 职责：管理支付流水、模拟银行交互、回调处理。
5. EmailService(模拟基础设施 不用真正做邮件或者短信服务):
  - 职责：通过 Log 打印模拟阿里云短信推送、邮件通知、支付接口回调。
3.2 流量链路 (Traffic Flow)
1. Nginx 层：接收公网请求，通过 upstream 负载均衡到两个 Gateway 实例。
2. Gateway 层：执行基于 Redis 的滑动窗口限流，非法请求直接拦截。
3. Service 层：Seckill 服务通过 Lua 脚本与 Redis 交互，扣减成功后发送消息至 MQ。
4. Async 层：Order 服务消费消息并落库。

# 高并发核心方案设计 (Core Implementation)
4.1 库存预扣与防止超卖 (Atomic Lua)
- 方案：将库存存入 Redis 散列。秒杀开始时，所有请求不直接访问数据库。
- Lua 脚本逻辑：
- Lua
-- 1. 检查用户是否已购买 (一人一单)if redis.call('SISMEMBER', buy_list, userId) == 1 then return -1 end-- 2. 检查库存local stock = tonumber(redis.call('get', stock_key))
if stock <= 0 then return 0 end-- 3. 扣减
redis.call('DECR', stock_key)
redis.call('SADD', buy_list, userId)
return 1
4.2 缓存优化 (Cache Strategy)
- 多级缓存：Caffeine (JVM 缓存) 存储极其频繁的商品元数据，Redis 存储动态库存。
- 缓存击穿：使用 Redisson 分布式锁，确保只有一个请求去数据库加载冷数据。
- 缓存预热：系统启动时，通过 CommandLineRunner 将秒杀商品信息加载进 Redis。
4.3 滑动窗口限流 (Rate Limiting)
- 实现：在 Gateway 拦截器中，利用 Redis 的 zset 结构存储时间戳。
- 逻辑：每次请求删除 (now - 1s) 之前的记录，计算 zcard 是否超过阈值。
4.4 支付与关单的并发控制
- 挑战：用户支付的同时，定时任务触发关单。
- 方案：
  1. RocketMQ 延迟消息：在 Order 服务中实现延迟关单。订单创建时发送 RocketMQ 延迟消息（30分钟）。消费者收到消息后，需校验订单支付状态。如果未支付，利用分布式锁确保此时没有支付回调在处理，然后执行关单并回滚 Redis 库存。
  2. 分布式锁：在执行“关单”或“支付成功处理”前，必须争抢 order_lock:orderId。


# 说明
## 补充
- 上述文档仅仅只是目前我对项目的理解和希望，不要当成最终版本，后续我和你都可以根据实际情况进行调整以及扩展。
- 项目的核心目标是学习，所以我们不做JWT鉴权或者真实的邮件以及付款服务，重点是模拟流程，学习高并发场景的处理和设计。

## 文档导航
docs文件夹包括了具体的设计细节
docs/
- /API.md - API 设计
- /ARCHITECTURE.md - 系统架构设计
- /CACHE_DESIGN.md - 缓存设计
- /DB_DESIGN.md - 数据库设计
- /DOMAIN.md - 具体的商业逻辑设计
- /MQ_DESIGN.md - 消息队列设计

同样，以上文档仅仅只是目前我对项目的理解和希望，不要当成最终版本，后续我和你都可以根据实际情况进行调整以及扩展。


