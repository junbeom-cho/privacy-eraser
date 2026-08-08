#!/usr/bin/env bash
# HR 샘플 스키마 설치. 컨테이너 안에서 실행합니다.
#
# 공식 이미지는 DB 를 처음 만들 때 /opt/oracle/scripts/setup/ 안의 스크립트를 한 번 실행합니다.
# 이 파일이 거기 마운트되어 있으므로, 볼륨을 비우고 올리면 자동으로 설치됩니다.
# 이미 만들어진 DB 에 넣으려면 직접 실행하세요:
#
#   docker exec oracle23ai /opt/oracle/scripts/setup/install-hr.sh
#
set -euo pipefail

readonly PDB="${ORACLE_PDB:-FREEPDB1}"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 하위 스크립트가 SPOOL 로 로그를 쓰므로 쓰기 가능한 곳에서 실행합니다.
# 마운트는 읽기 전용입니다.
cd /tmp

echo "[hr] ${PDB} 에 HR 샘플 스키마를 설치합니다."
sqlplus -S "system/${ORACLE_PWD}@//localhost:1521/${PDB}" "@${SCRIPT_DIR}/01_install_hr.sql"

echo "[hr] 설치 결과:"
sqlplus -S "system/${ORACLE_PWD}@//localhost:1521/${PDB}" <<'SQL'
SET HEADING OFF FEEDBACK OFF PAGESIZE 0
SELECT '  tables : '||COUNT(*) FROM all_tables WHERE owner='HR';
SELECT '  employees rows : '||COUNT(*) FROM hr.employees;
EXIT
SQL
