#!/bin/bash
set -euo pipefail

export PGPASSWORD="${REPLICA_PASSWORD:-}"

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "standby-init: PGDATA empty, waiting for primary ${PRIMARY_HOST}..."
  for i in $(seq 1 60); do
    if pg_isready -h "$PRIMARY_HOST" -p 5432 -U "$REPLICA_USER" -d postgres >/dev/null 2>&1; then
      break
    fi
    echo "standby-init: primary not ready (attempt $i), retrying..."
    sleep 2
  done
  if ! pg_isready -h "$PRIMARY_HOST" -p 5432 -U "$REPLICA_USER" -d postgres >/dev/null 2>&1; then
    echo "standby-init: primary ${PRIMARY_HOST} is not reachable, aborting" >&2
    exit 1
  fi

  echo "standby-init: running pg_basebackup from ${PRIMARY_HOST}..."
  mkdir -p "$PGDATA"
  pg_basebackup -h "$PRIMARY_HOST" -p 5432 -U "$REPLICA_USER" -D "$PGDATA" -Fp -Xs -P

  touch "$PGDATA/standby.signal"
  cat >> "$PGDATA/postgresql.auto.conf" <<EOF
primary_conninfo = 'host=${PRIMARY_HOST} port=5432 user=${REPLICA_USER} password=${REPLICA_PASSWORD} application_name=${APPLICATION_NAME}'
EOF
  chown -R postgres:postgres "$PGDATA"
  echo "standby-init: base backup done, starting as standby (application_name=${APPLICATION_NAME})"
fi

exec docker-entrypoint.sh "$@"
