-- rollback.lua
-- 库存回滚脚本

-- KEYS[1]: 库存 Key (stock:seckill:{skuId})
-- KEYS[2]: 用户购买记录 Key (user:bought:{skuId})
-- ARGV[1]: 回滚数量
-- ARGV[2]: userId

-- 1. 回滚库存
redis.call('INCRBY', KEYS[1], ARGV[1])

-- 2. 移除用户购买记录
redis.call('SREM', KEYS[2], ARGV[2])

return 1
