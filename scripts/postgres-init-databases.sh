#!/bin/bash
# Crea las bases lógicas adicionales que usará el sistema nuevo (docs/04-roadmap.md, v0.4).
# Postgres ya crea POSTGRES_DB (definida en .env) por su cuenta; cada base adicional queda vacía
# hasta que su servicio arranque y Liquibase la migre. Ver docs/06-entorno-local-tilt.md.
#
# "ukun_gateway" es de v1.0 (ADR-0024, issue #118): el gateway deja de guardar la sesión en el heap
# y pasa a tener esquema propio. Es el coste más alto de esa decisión y está escrito allí.
#
# Este script SOLO crea bases vacías. Ningún DDL aquí (skill `liquibase`, regla 3): el esquema lo
# crea Liquibase al arrancar cada servicio, que es lo que el init.sql legacy no permitía.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE ukun_auth'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ukun_auth')\gexec

    SELECT 'CREATE DATABASE ukun_gateway'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ukun_gateway')\gexec
EOSQL
