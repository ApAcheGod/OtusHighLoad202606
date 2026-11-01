#!/usr/bin/env bash
# Добавить новый воркер в работающий кластер Citus и разложить на него шарды.
#
# Воркер citus-worker-4 объявлен в docker-compose.yml под профилем `extra`,
# поэтому поднимается отдельной командой и не занимает ресурсы в обычном режиме.
#
# Usage: scripts/citus/add_worker.sh [worker-host] [port]
#        scripts/citus/add_worker.sh                      # citus-worker-4:5432
#        scripts/citus/add_worker.sh citus-worker-5 5432
set -euo pipefail

WORKER_HOST="${1:-citus-worker-4}"
WORKER_PORT="${2:-5432}"
COORDINATOR="${COORDINATOR:-citus-coordinator}"
PGUSER="${PGUSER:-otus}"
PGDB="${PGDB:-otus_highload}"

# Профиль extra нужен, чтобы docker compose видел сервис citus-worker-4.
dc() {
  docker compose --profile extra "$@"
}

psql_on() {
  local host="$1"
  shift
  dc exec -T "$host" psql -U "$PGUSER" -d "$PGDB" -v ON_ERROR_STOP=1 "$@"
}

echo "==> Поднимаем $WORKER_HOST (профиль extra)"
dc up -d "$WORKER_HOST"

echo "==> Ждём готовности $WORKER_HOST"
ready=false
for _ in $(seq 1 40); do
  if dc exec -T "$WORKER_HOST" pg_isready -U "$PGUSER" -d "$PGDB" >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 3
done
if [ "$ready" != true ]; then
  echo "ОШИБКА: $WORKER_HOST не стал готов к подключению за 120 с — прерываю, регистрация не выполнялась." >&2
  exit 1
fi

echo "==> Проверяем предпосылки онлайн-ребалансировки"
# wal_level=logical нужен для переноса через logical replication — на ВСЕХ нодах,
# не только на координаторе: перенос шарда выполняется между воркерами.
check_wal_level() {
  local node="$1"
  psql_on "$node" -c "SHOW wal_level;" | grep -q logical || {
    echo "ОШИБКА: на $node wal_level != logical — онлайн-перенос невозможен." >&2
    echo "         Поднимите кластер с -c wal_level=logical (см. docker-compose.yml)." >&2
    exit 1
  }
}
check_wal_level "$COORDINATOR"
check_wal_level "$WORKER_HOST"

echo "==> Сообщаем $WORKER_HOST, где находится координатор"
psql_on "$WORKER_HOST" -c "SELECT citus_set_coordinator_host('$COORDINATOR', 5432);"

echo "==> Регистрируем $WORKER_HOST:$WORKER_PORT на координаторе (идемпотентно)"
psql_on "$COORDINATOR" -c \
  "SELECT citus_add_node('$WORKER_HOST', $WORKER_PORT)
   WHERE NOT EXISTS (
     SELECT 1 FROM pg_dist_node WHERE nodename = '$WORKER_HOST' AND nodeport = $WORKER_PORT
   );"

echo "==> Новый список нод"
psql_on "$COORDINATOR" -c \
  "SELECT nodeid, nodename, nodeport, noderole, isactive, shouldhaveshards FROM pg_dist_node ORDER BY nodename;"

echo "==> Ребалансировка (онлайн, через logical replication)"
# users/friends/posts — одна colocation-группа: достаточно одного вызова.
psql_on "$COORDINATOR" -c "SELECT rebalance_table_shards('users');"
psql_on "$COORDINATOR" -c "SELECT rebalance_table_shards('dialog_messages');"

echo "==> Итоговое размещение шардов"
psql_on "$COORDINATOR" -c \
  "SELECT nodename, count(*) AS shards, pg_size_pretty(sum(shard_size)) AS data
   FROM citus_shards GROUP BY 1 ORDER BY 1;"

echo "==> Готово. Новый воркер участвует в кластере, приложение перезапускать не нужно."
