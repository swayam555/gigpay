-- One Postgres instance, one database per service (database-per-service pattern).
CREATE DATABASE earnings;
CREATE DATABASE ledger;
CREATE DATABASE coach;
\connect coach
CREATE EXTENSION IF NOT EXISTS vector;
