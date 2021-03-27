#!/bin/bash

set -e

# see testname here https://github.com/akopytov/sysbench/tree/master/src/lua

SB_THREAD=${THREAD:-16}
SB_DURATION_SEC=30
SB_PG_USER=${DB_USER:-postgres}
SB_PG_PASSWORD=${DB_PASS:-pss}
SB_PG_DB=bench_sc1
SB_PG_HOST=${DB_HOST:-127.0.0.1}

PGPASSWORD="${SB_PG_PASSWORD}" psql --host=${SB_PG_HOST} --username=${SB_PG_USER} -c "drop database if exists ${SB_PG_DB};"
PGPASSWORD="${SB_PG_PASSWORD}" psql --host=${SB_PG_HOST} --username=${SB_PG_USER} -c "create database ${SB_PG_DB};"

OPT="--threads=${SB_THREAD} --time=${SB_DURATION_SEC} --histogram=on --pgsql-host=${SB_PG_HOST} --pgsql-user=${SB_PG_USER} --pgsql-password=${SB_PG_PASSWORD} --pgsql-db=${SB_PG_DB} --db-driver=pgsql --rand-seed=1 --rand-type=uniform --report-interval=2"

exec_scene() {
  sysbench ${1} ${OPT} cleanup || true
  sysbench ${1} ${OPT} prepare
  sysbench ${1} ${OPT} run
}

# exec_scene "oltp_insert --table-size=10000 --create-secondary=off --auto-inc=on --delete-inserts=0 --index-updates=0 --non-index-updates=0 --skip-trx=on"

# exec_scene "oltp_insert --table-size=10000 --create-secondary=on --auto-inc=on --delete-inserts=0 --index-updates=0 --non-index-updates=0 --skip-trx=on"

# exec_scene "oltp_insert --table-size=10000 --create-secondary=on --auto-inc=off --delete-inserts=0 --index-updates=0 --non-index-updates=0 --skip-trx=on"

# exec_scene "oltp_update_index --table-size=10000 --create-secondary=off --auto-inc=on --delete-inserts=0 --index-updates=1 --non-index-updates=1 --skip-trx=on"

exec_scene "oltp_update_non_index --table-size=10000 --create-secondary=off --auto-inc=on --delete-inserts=0 --index-updates=1 --non-index-updates=1 --skip-trx=on"
