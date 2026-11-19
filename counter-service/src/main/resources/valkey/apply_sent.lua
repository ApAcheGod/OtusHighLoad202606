local sagaState = redis.call('HGET', KEYS[3], 'state')
local counter = redis.call('GET', KEYS[1]) or '0'
if sagaState == 'APPLIED' or sagaState == 'COMPENSATING' or sagaState == 'COMPENSATED' then
  return {'DUPLICATE', counter}
end

local version = tonumber(ARGV[1])
local current = redis.call('GET', KEYS[2])
if current then
  local currentVersion = tonumber(string.match(current, ':(%d+)$'))
  if currentVersion >= version then
    if currentVersion > version or string.sub(current, 1, 4) == 'READ' then
      return {'STALE', counter}
    end
    return {'DUPLICATE', counter}
  end
end

redis.call('SET', KEYS[2], 'UNREAD:' .. version)
local value = redis.call('INCR', KEYS[1])
redis.call('HSET', KEYS[3], 'state', 'APPLIED', 'event_id', ARGV[2], 'version', version,
  'message_id', ARGV[5], 'updated_at_ms', ARGV[4])
redis.call('EXPIRE', KEYS[3], 604800)
return {'APPLIED', value}
