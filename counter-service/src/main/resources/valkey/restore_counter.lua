redis.call('SET', KEYS[1], ARGV[1])
for i = 2, #KEYS do
  local stateIndex = (i - 2) * 2 + 2
  local snapshotState = ARGV[stateIndex]
  local snapshotVersion = tonumber(ARGV[stateIndex + 1])
  local current = redis.call('GET', KEYS[i])
  local replace = false
  if not current then
    replace = true
  else
    local currentVersion = tonumber(string.match(current, ':(%d+)$'))
    replace = currentVersion < snapshotVersion
  end
  if replace then
    redis.call('SET', KEYS[i], snapshotState .. ':' .. snapshotVersion)
    if snapshotState == 'READ' then
      redis.call('EXPIRE', KEYS[i], 604800)
    end
  end
end
return tonumber(redis.call('GET', KEYS[1]))
