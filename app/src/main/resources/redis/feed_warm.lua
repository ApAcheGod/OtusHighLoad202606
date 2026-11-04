for i = 3, #ARGV, 3 do
    redis.call('ZADD', KEYS[1], ARGV[i + 2], ARGV[i])
    redis.call('HSET', KEYS[2], ARGV[i], ARGV[i + 1])
end
redis.call('SET', KEYS[3], '1', 'EX', ARGV[2])
local excess = redis.call('ZCARD', KEYS[1]) - tonumber(ARGV[1])
local trimmed = 0
if excess > 0 then
    local old = redis.call('ZRANGE', KEYS[1], 0, excess - 1)
    for _, id in ipairs(old) do
        redis.call('ZREM', KEYS[1], id)
        redis.call('HDEL', KEYS[2], id)
        trimmed = trimmed + 1
    end
end
redis.call('EXPIRE', KEYS[1], ARGV[2])
redis.call('EXPIRE', KEYS[2], ARGV[2])
return trimmed
