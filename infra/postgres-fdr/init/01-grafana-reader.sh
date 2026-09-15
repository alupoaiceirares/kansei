#!/bin/bash
# Only runs on a fresh volume (docker-entrypoint-initdb.d convention) - an existing volume needs this run by hand once.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE grafana_reader LOGIN PASSWORD '$FDR_GRAFANA_DB_PASSWORD';
    GRANT CONNECT ON DATABASE "$POSTGRES_DB" TO grafana_reader;
    GRANT USAGE ON SCHEMA public TO grafana_reader;
    ALTER DEFAULT PRIVILEGES FOR ROLE "$POSTGRES_USER" IN SCHEMA public GRANT SELECT ON TABLES TO grafana_reader;
EOSQL
