#!/bin/bash
# Primary (user-db) entrypoint wrapper for HW-9.
# - Fresh volumes get the "replica" role and replication HBA via initdb.d.
# - Existing volumes (created before HW-9) lack both, so they are also
#   ensured idempotently here as soon as the server accepts connections.
set -euo pipefail

(
  for i in $(seq 1 60); do
    if pg_isready -U otus -d users >/dev/null 2>&1; then
      if ! psql -U otus -d users -tAc "SELECT 1 FROM pg_roles WHERE rolname='replica'" | grep -q 1; then
        echo "user-db-primary: creating replication role 'replica'"
        psql -U otus -d users -c "CREATE ROLE replica WITH LOGIN REPLICATION PASSWORD 'replica_pass'"
      fi
      HBA_LINE='host replication all all scram-sha-256'
      if ! grep -qF "$HBA_LINE" "$PGDATA/pg_hba.conf"; then
        echo "user-db-primary: appending replication rule to pg_hba.conf"
        echo "$HBA_LINE" >> "$PGDATA/pg_hba.conf"
        psql -U otus -d users -c "SELECT pg_reload_conf()" >/dev/null
      fi
      exit 0
    fi
    sleep 2
  done
  echo "user-db-primary: server did not become ready, role/hba check skipped" >&2
) &

exec docker-entrypoint.sh "$@"
