CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

CREATE ROLE monitor WITH LOGIN PASSWORD 'monitor_pass';
GRANT pg_read_all_stats TO monitor;
GRANT CONNECT ON DATABASE otus_highload TO monitor;
GRANT USAGE ON SCHEMA public TO monitor;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO monitor;

CREATE ROLE replica WITH LOGIN REPLICATION PASSWORD 'replica_pass';
