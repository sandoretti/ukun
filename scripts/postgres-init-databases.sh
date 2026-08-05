#!/bin/bash
# Crea las bases lógicas adicionales que usará el sistema nuevo (docs/04-roadmap.md, v0.4).
# Postgres ya crea POSTGRES_DB (definida en .env) por su cuenta; esta base "ukun_auth"
# queda vacía hasta que exista services/ukun-auth. Ver docs/06-entorno-local-tilt.md.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE ukun_auth'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ukun_auth')\gexec
EOSQL
