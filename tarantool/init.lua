local clock = require('clock')
local uuid = require('uuid')
local utf8 = require('utf8')

box.cfg {
    listen = 3301,
    memtx_memory = 2 * 1024 * 1024 * 1024,
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
    {name = 'read_at_ns', type = 'integer', is_nullable = true},
    {name = 'operation_version', type = 'integer', is_nullable = true},
    {name = 'counter_status', type = 'string', is_nullable = true},
    {name = 'updated_at_ns', type = 'integer', is_nullable = true},
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
messages:create_index('recipient_created', {
    if_not_exists = true,
    unique = false,
    parts = {
        {field = 'to_user_id', type = 'uuid'},
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

local outbox = box.schema.space.create('dialog_outbox', {if_not_exists = true})
outbox:format({
    {name = 'event_id', type = 'uuid'},
    {name = 'saga_id', type = 'uuid'},
    {name = 'message_id', type = 'uuid'},
    {name = 'event_type', type = 'string'},
    {name = 'payload', type = 'map'},
    {name = 'created_at_ns', type = 'integer'},
    {name = 'published_at_ns', type = 'integer', is_nullable = true},
    {name = 'attempts', type = 'integer'},
    {name = 'last_error', type = 'string', is_nullable = true},
})
outbox:create_index('primary', {
    if_not_exists = true,
    parts = {{field = 'event_id', type = 'uuid'}},
})
outbox:create_index('created', {
    if_not_exists = true,
    unique = true,
    parts = {
        {field = 'created_at_ns', type = 'integer'},
        {field = 'event_id', type = 'uuid'},
    },
})

local inbox = box.schema.space.create('dialog_inbox', {if_not_exists = true})
inbox:format({
    {name = 'event_id', type = 'uuid'},
    {name = 'saga_id', type = 'uuid'},
    {name = 'created_at_ns', type = 'integer'},
})
inbox:create_index('primary', {
    if_not_exists = true,
    parts = {{field = 'event_id', type = 'uuid'}},
})

local function bad_request(message)
    error('BAD_REQUEST: ' .. message, 0)
end

local function forbidden(message)
    error('FORBIDDEN: ' .. message, 0)
end

local function not_found(message)
    error('NOT_FOUND: ' .. message, 0)
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

local function version_of(message)
    return message.operation_version or 1
end

local function read_at_of(message)
    return message.read_at_ns or 0
end

local function counter_status_of(message)
    return message.counter_status or 'ACTIVE'
end

local function put_outbox(event_type, saga_id, message_id, user_id, version)
    local event_id = uuid.new()
    local now = clock.time64()
    local payload = {
        event_id = tostring(event_id),
        saga_id = tostring(saga_id),
        message_id = tostring(message_id),
        user_id = user_id,
        version = version,
        type = event_type,
        occurred_at_ns = tostring(now),
    }
    outbox:insert({event_id, saga_id, message_id, event_type, payload, now, nil, 0, nil})
    return event_id
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
        local saga_id = uuid.new()
        local now = clock.time64()
        messages:insert({message_id, id, from_id, to_id, text, now, 0, 1, 'ACTIVE', now})
        put_outbox('MESSAGE_SENT', saga_id, message_id, to_value, 1)
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
                read_at_ns = tostring(read_at_of(message)),
                operation_version = version_of(message),
                counter_status = counter_status_of(message),
            })
            if limit ~= nil and #result >= limit then
                break
            end
        end
    end
    return result
end

function dialog_mark_read(user_value, peer_value, message_value)
    local user_id = parse_uuid(user_value, 'user_id')
    parse_uuid(peer_value, 'with_user_id')
    local message_id = parse_uuid(message_value, 'message_id')
    local expected_dialog_id = dialog_id(user_value, peer_value)

    return box.atomic(function()
        local message = messages:get(message_id)
        if message == nil then
            not_found('Message not found')
        end
        if message.dialog_id ~= expected_dialog_id then
            not_found('Message not found in dialog')
        end
        if message.to_user_id ~= user_id then
            forbidden('Only recipient can mark message read')
        end
        if counter_status_of(message) == 'COUNTER_FAILED' then
            bad_request('Message excluded from unread counter')
        end
        if read_at_of(message) ~= 0 then
            return {status = 'ALREADY_READ', version = version_of(message)}
        end

        local now = clock.time64()
        local next_version = version_of(message) + 1
        local saga_id = uuid.new()
        messages:update(message_id, {
            {'=', 'read_at_ns', now},
            {'=', 'operation_version', next_version},
            {'=', 'updated_at_ns', now},
        })
        put_outbox('MESSAGE_READ', saga_id, message_id, user_value, next_version)
        return {status = 'MARKED', version = next_version}
    end)
end

function dialog_outbox_batch(limit_value)
    local limit = validate_limit(limit_value)
    if limit == nil then
        limit = 100
    end
    local result = {}
    for _, event in outbox.index.created:pairs() do
        if event.published_at_ns == nil then
            table.insert(result, event.payload)
            if #result >= limit then
                break
            end
        end
    end
    return result
end

function dialog_outbox_mark_published(event_value)
    local event_id = parse_uuid(event_value, 'event_id')
    local event = outbox:get(event_id)
    if event == nil then
        not_found('Outbox event not found')
    end
    if event.published_at_ns == nil then
        outbox:update(event_id, {{'=', 'published_at_ns', clock.time64()}})
    end
    return 'OK'
end

function dialog_unread_snapshot(user_value)
    local user_id = parse_uuid(user_value, 'user_id')
    local states = {}
    local count = 0
    for _, message in messages.index.recipient_created:pairs({user_id}, {iterator = 'GE'}) do
        if message.to_user_id ~= user_id then
            break
        end
        local unread = read_at_of(message) == 0 and counter_status_of(message) ~= 'COUNTER_FAILED'
        if unread then
            count = count + 1
        end
        table.insert(states, {
            id = tostring(message.id),
            state = unread and 'UNREAD' or 'READ',
            version = version_of(message),
        })
    end
    return {count = count, messages = states}
end

function dialog_apply_compensation(event_value, saga_value, message_value, version, action)
    local event_id = parse_uuid(event_value, 'event_id')
    local saga_id = parse_uuid(saga_value, 'saga_id')
    local message_id = parse_uuid(message_value, 'message_id')
    if type(version) ~= 'number' or version < 1 or version % 1 ~= 0 then
        bad_request('version must be a positive integer')
    end
    if action ~= 'MARK_MESSAGE_NOT_UNREAD' and action ~= 'RESTORE_UNREAD_MESSAGE' then
        bad_request('Unknown compensation action')
    end

    return box.atomic(function()
        if inbox:get(event_id) ~= nil then
            return 'DUPLICATE'
        end
        local message = messages:get(message_id)
        if message == nil then
            not_found('Message not found')
        end

        local result = 'APPLIED'
        local now = clock.time64()
        if version_of(message) ~= version then
            result = 'STALE'
        elseif action == 'MARK_MESSAGE_NOT_UNREAD' then
            messages:update(message_id, {
                {'=', 'counter_status', 'COUNTER_FAILED'},
                {'=', 'updated_at_ns', now},
            })
        else
            messages:update(message_id, {
                {'=', 'read_at_ns', 0},
                {'=', 'counter_status', 'ACTIVE'},
                {'=', 'updated_at_ns', now},
            })
        end

        inbox:insert({event_id, saga_id, now})
        put_outbox('COMPENSATION_APPLIED', saga_id, message_id, tostring(message.to_user_id), version)
        return result
    end)
end
