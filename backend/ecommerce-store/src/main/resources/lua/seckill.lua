-- seckill.lua
-- 库存预扣 + 一人一单，原子操作

-- KEYS[1]: 库存 Key
-- KEYS[2]: 用户购买记录 Key
-- ARGV[1]: 购买数量
-- ARGV[2]: userId

-- 0. 参数预处理
local buyAmount = tonumber(ARGV[1])
if not buyAmount then
    return -3 -- 错误：购买数量参数缺失或非数字
end

-- 1. 检查用户是否已购买
local alreadyBought = redis.call('SISMEMBER', KEYS[2], ARGV[2])
if alreadyBought == 1 then
    return -1  -- 已购买
end

-- 2. 检查库存
local stockStr = redis.call('GET', KEYS[1])
if not stockStr then
    return -2  -- 库存未初始化
end

local stock = tonumber(stockStr)
-- 关键修复：确保比较的两侧都不是 nil
if not stock or stock < buyAmount then
    return 0   -- 库存不足或解析失败
end

-- 3. 原子扣减库存
redis.call('DECRBY', KEYS[1], buyAmount)

-- 4. 标记用户已购买
redis.call('SADD', KEYS[2], ARGV[2])

return 1  -- 成功
