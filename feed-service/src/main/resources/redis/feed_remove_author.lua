local entries = redis.call('HGETALL', KEYS[2])
local removed = 0
for i = 1, #entries, 2 do
    if entries[i + 1] == ARGV[1] then
        redis.call('ZREM', KEYS[1], entries[i])
        redis.call('HDEL', KEYS[2], entries[i])
        removed = removed + 1
    end
end
return removed
