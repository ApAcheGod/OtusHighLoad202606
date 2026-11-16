-- Replication role for streaming replicas of the users database (HW-9).
CREATE ROLE replica WITH LOGIN REPLICATION PASSWORD 'replica_pass';
