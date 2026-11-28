local target = ARGV[1]
local current = redis.call('HGET', KEYS[1], 'state')

if target == 'COMPENSATING' then
  if current == 'APPLIED' or current == 'COMPENSATED' then
    return current
  end
elseif target == 'COMPENSATED' then
  if current == 'COMPENSATED' then
    return current
  end
else
  return redis.error_reply('Unsupported saga state')
end

redis.call('HSET', KEYS[1], 'state', target, 'event_id', ARGV[2], 'version', ARGV[3],
  'message_id', ARGV[4], 'type', ARGV[5], 'error', ARGV[6], 'updated_at_ms', ARGV[7])
redis.call('EXPIRE', KEYS[1], 604800)
return target
