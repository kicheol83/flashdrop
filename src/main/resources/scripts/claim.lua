local stockKey = KEYS[1]
local claimedKey = KEYS[2]
local userId = ARGV[1]

if redis.call('SISMEMBER', claimedKey, userId) == 1 then
    return -2
end

local stock = redis.call('GET', stockKey)
if not stock then
    return -1
end

if tonumber(stock) <= 0 then
    return 0
end

redis.call('DECR', stockKey)
redis.call('SADD', claimedKey, userId)
return 1
