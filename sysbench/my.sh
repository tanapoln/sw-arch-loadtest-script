#!/bin/bash

set -e

# see testname here https://github.com/akopytov/sysbench/tree/master/src/lua

SB_THREAD=${THREAD:-16}
SB_DURATION_SEC=30
SB_MYSQL_USER=${DB_USER:-root}
SB_MYSQL_PASSWORD=${DB_PASS:-pass}
SB_MYSQL_DB=bench_sc1
SB_MYSQL_HOST=${DB_HOST:-127.0.0.1}

mysql --host=${SB_MYSQL_HOST} --user=${SB_MYSQL_USER} --password=${SB_MYSQL_PASSWORD} -e "create database if not exists ${SB_MYSQL_DB};"

OPT="--threads=${SB_THREAD} --time=${SB_DURATION_SEC} --histogram=on --mysql-host=${SB_MYSQL_HOST} --mysql-user=${SB_MYSQL_USER} --mysql-password=${SB_MYSQL_PASSWORD} --mysql-db=${SB_MYSQL_DB} --db-driver=mysql --rand-seed=1 --rand-type=uniform --report-interval=2"

if [[ ! -z "${DEBUG}" ]]; then
  OPT="${OPT} --mysql-debug=on"
fi

exec_scene() {
  sysbench ${1} ${OPT} cleanup || true
  sysbench ${1} ${OPT} prepare
  sysbench ${1} ${OPT} run
}

exec_scene "oltp_insert --table-size=10000 --create-secondary=off --auto-inc=on --delete-inserts=0 --index-updates=0 --non-index-updates=0 --skip-trx=on"

# exec_scene "oltp_insert --table-size=10000 --create-secondary=on --auto-inc=on --delete-inserts=0 --index-updates=0 --non-index-updates=0 --skip-trx=on"

# exec_scene "oltp_insert --table-size=10000 --create-secondary=on --auto-inc=off --delete-inserts=0 --index-updates=0 --non-index-updates=0 --skip-trx=on"
