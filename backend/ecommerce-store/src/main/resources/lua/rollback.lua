-- rollback.lua
-- 库存回滚脚本 + 幂等校验

-- KEYS[1]: 库存 Key (stock:seckill:{skuId})
-- KEYS[2]: 用户购买记录 Key (user:bought:{skuId})
-- KEYS[3]: 回滚记录 Key (stock:rollback:record:{orderNo})
-- ARGV[1]: 回滚数量
-- ARGV[2]: userId
-- ARGV[3]: orderNo

-- 1. 幂等校验：检查是否已处理过
local alreadyRollback = redis.call('EXISTS', KEYS[3])
if alreadyRollback == 1 then
    return 0  -- 已恢复过，跳过
end

-- 2. 回滚库存
redis.call('INCRBY', KEYS[1], ARGV[1])

-- 3. 移除用户购买记录
redis.call('SREM', KEYS[2], ARGV[2])

-- 4. 标记该订单已恢复（防止重复）
redis.call('SET', KEYS[3], '1', 'EX', 86400)

return 1  -- 成功
