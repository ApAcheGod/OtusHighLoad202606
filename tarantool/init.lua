local clock = require('clock')
local uuid = require('uuid')
local utf8 = require('utf8')

box.cfg {
    listen = 3301,
    memtx_memory = 512 * 1024 * 1024,
}

box.schema.user.create('app', {password = 'app_pass', if_not_exists = true})
box.schema.user.grant('app', 'read,write,execute', 'universe', nil, {if_not_exists = true})

local messages = box.schema.space.create('dialog_messages', {if_not_exists = true})
messages:format({
    {name = 'id', type = 'uuid'},
    {name = 'dialog_id', type = 'string'},
    {name = 'from_user_id', type = 'uuid'},
    {name = 'to_user_id', type = 'uuid'},
    {name = 'text', type = 'string'},
    {name = 'created_at_ns', type = 'integer'},
})
messages:create_index('primary', {
    if_not_exists = true,
    parts = {{field = 'id', type = 'uuid'}},
})
messages:create_index('dialog_created', {
    if_not_exists = true,
    unique = true,
    parts = {
        {field = 'dialog_id', type = 'string'},
        {field = 'created_at_ns', type = 'integer'},
        {field = 'id', type = 'uuid'},
    },
})

local dedup = box.schema.space.create('dialog_client_messages', {if_not_exists = true})
dedup:format({
    {name = 'dialog_id', type = 'string'},
    {name = 'client_message_id', type = 'uuid'},
    {name = 'message_id', type = 'uuid'},
})
dedup:create_index('primary', {
    if_not_exists = true,
    parts = {
        {field = 'dialog_id', type = 'string'},
        {field = 'client_message_id', type = 'uuid'},
    },
})

local function bad_request(message)
    error('BAD_REQUEST: ' .. message, 0)
end

local function parse_uuid(value, name)
    if type(value) ~= 'string' then
        bad_request(name .. ' must be a UUID string')
    end
    local ok, parsed = pcall(uuid.fromstr, value)
    if not ok or parsed == nil then
        bad_request(name .. ' must be a UUID string')
    end
    return parsed
end

local function dialog_id(a, b)
    if a == b then
        bad_request('Cannot use the same user twice')
    end
    if a < b then
        return a .. ':' .. b
    end
    return b .. ':' .. a
end

local function validate_text(text)
    if type(text) ~= 'string' or text:match('^%s*$') then
        bad_request('text must not be blank')
    end
    local length = utf8.len(text)
    if length == nil then
        bad_request('text must be valid UTF-8')
    end
    if length > 4096 then
        bad_request('text must not exceed 4096 characters')
    end
end

local function validate_offset(offset)
    if offset == nil then
        return 0
    end
    if type(offset) ~= 'number' or offset < 0 or offset % 1 ~= 0 then
        bad_request('offset must be a non-negative integer')
    end
    return offset
end

local function validate_limit(limit)
    if limit == nil then
        return nil
    end
    if type(limit) ~= 'number' or limit < 1 or limit > 100 or limit % 1 ~= 0 then
        bad_request('limit must be between 1 and 100')
    end
    return limit
end

function dialog_send(from_value, to_value, text, client_value)
    local from_id = parse_uuid(from_value, 'from_user_id')
    local to_id = parse_uuid(to_value, 'to_user_id')
    validate_text(text)
    local id = dialog_id(from_value, to_value)
    local client_id = nil
    if client_value ~= nil then
        client_id = parse_uuid(client_value, 'client_message_id')
    end

    return box.atomic(function()
        if client_id ~= nil then
            local existing = dedup:get({id, client_id})
            if existing ~= nil then
                return tostring(existing.message_id)
            end
        end

        local message_id = uuid.new()
        messages:insert({message_id, id, from_id, to_id, text, clock.time64()})
        if client_id ~= nil then
            dedup:insert({id, client_id, message_id})
        end
        return tostring(message_id)
    end)
end

function dialog_list(user_value, peer_value, offset_value, limit_value)
    parse_uuid(user_value, 'user_id')
    parse_uuid(peer_value, 'with_user_id')
    local id = dialog_id(user_value, peer_value)
    local offset = validate_offset(offset_value)
    local limit = validate_limit(limit_value)
    local result = {}
    local skipped = 0

    for _, message in messages.index.dialog_created:pairs({id}, {iterator = 'GE'}) do
        if message.dialog_id ~= id then
            break
        end
        if skipped < offset then
            skipped = skipped + 1
        else
            table.insert(result, {
                id = tostring(message.id),
                from = tostring(message.from_user_id),
                to = tostring(message.to_user_id),
                text = message.text,
                created_at_ns = tostring(message.created_at_ns),
            })
            if limit ~= nil and #result >= limit then
                break
            end
        end
    end
    return result
end
