# Allow streaming replication from any host on the docker network
# for the "replica" role (default image pg_hba.conf only allows
# replication from localhost). POSIX-compatible on purpose: this file
# is sourced or executed by docker-entrypoint (no shebang).

set -euo pipefail

LINE='host replication all all scram-sha-256'
if ! grep -qF "$LINE" "$PGDATA/pg_hba.conf"; then
  echo "$LINE" >> "$PGDATA/pg_hba.conf"
fi