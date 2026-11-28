local sagaState = redis.call('HGET', KEYS[3], 'state')
local counter = redis.call('GET', KEYS[1]) or '0'
if sagaState == 'APPLIED' or sagaState == 'COMPENSATING' or sagaState == 'COMPENSATED' then
  return {'DUPLICATE', counter}
end

local version = tonumber(ARGV[1])
local current = redis.call('GET', KEYS[2])
local result = 'APPLIED'
local value = tonumber(counter)

if current then
  local currentVersion = tonumber(string.match(current, ':(%d+)$'))
  if currentVersion > version then
    return {'STALE', counter}
  end
  if currentVersion == version then
    if string.sub(current, 1, 4) == 'READ' then
      result = 'DUPLICATE'
    else
      return {'STALE', counter}
    end
  elseif string.sub(current, 1, 6) == 'UNREAD' then
    if value > 0 then
      value = redis.call('DECR', KEYS[1])
    else
      result = 'INVARIANT_VIOLATION'
    end
    redis.call('SET', KEYS[2], 'READ:' .. version)
    redis.call('EXPIRE', KEYS[2], 604800)
  else
    return {'STALE', counter}
  end
else
  redis.call('SET', KEYS[2], 'READ:' .. version)
  redis.call('EXPIRE', KEYS[2], 604800)
  redis.call('SETNX', KEYS[1], 0)
  value = tonumber(redis.call('GET', KEYS[1]))
end

redis.call('HSET', KEYS[3], 'state', 'APPLIED', 'event_id', ARGV[2], 'version', version,
  'message_id', ARGV[5], 'updated_at_ms', ARGV[4])
redis.call('EXPIRE', KEYS[3], 604800)
return {result, value}
