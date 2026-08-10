#!/usr/bin/env bash
# infra/oracle/sample 의 Data Pump 덤프를 도커 Oracle 에 적재합니다.
#
#   ./infra/oracle/import-sample.sh
#
# 덤프는 CONTENT=DATA_ONLY 라 데이터만 들어 있습니다.
# VMISADM 테이블이 미리 만들어져 있어야 하고, 없으면 ORA-39034 가 납니다.
# DDL 스크립트를 먼저 실행하세요.
set -euo pipefail

CONTAINER=${CONTAINER:-oracle23ai}
SERVICE=${SERVICE:-FREEPDB1}
DBA=${DBA:-system/ptech6441}
SAMPLE_DIR="$(cd "$(dirname "$0")/sample" && pwd)"
REMOTE_DIR=/opt/oracle/oradata/dumps

docker exec "$CONTAINER" mkdir -p "$REMOTE_DIR"

for dmp in "$SAMPLE_DIR"/*.dmp; do
	name=$(basename "$dmp")
	# 이미 넣어둔 것은 다시 복사하지 않습니다. sccd 는 639MB 입니다.
	if docker exec "$CONTAINER" test -f "$REMOTE_DIR/$name"; then
		echo "이미 있음: $name"
	else
		echo "복사: $name"
		docker cp "$dmp" "$CONTAINER:$REMOTE_DIR/"
	fi
	# docker cp 는 호스트 소유자/권한을 그대로 가져와 oracle 이 못 읽습니다.
	docker exec -u root "$CONTAINER" chown oracle:oinstall "$REMOTE_DIR/$name"
	docker exec -u root "$CONTAINER" chmod 640 "$REMOTE_DIR/$name"
done

# 디렉터리 객체는 한 번만 만들면 됩니다.
docker exec -i "$CONTAINER" bash -lc "sqlplus -s $DBA@$SERVICE" <<SQL
CREATE OR REPLACE DIRECTORY DUMP_DIR AS '$REMOTE_DIR';
EXIT
SQL

for dmp in "$SAMPLE_DIR"/*.dmp; do
	name=$(basename "$dmp")
	job=IMP_$(basename "$name" .dmp)
	echo "== 적재: $name =="
	# 같은 이름의 마스터 테이블이 남아 있으면 다음 실행이 충돌합니다. 먼저 지웁니다.
	docker exec -i "$CONTAINER" bash -lc "sqlplus -s $DBA@$SERVICE" <<SQL > /dev/null 2>&1 || true
DROP TABLE SYSTEM.$job PURGE;
EXIT
SQL
	docker exec "$CONTAINER" bash -lc \
		"impdp $DBA@$SERVICE DIRECTORY=DUMP_DIR DUMPFILE=$name JOB_NAME=$job \
		 LOGFILE=${job}.log TABLE_EXISTS_ACTION=TRUNCATE" || true
done

echo
echo "적재 결과는 컨테이너의 $REMOTE_DIR/IMP_*.log 에 있습니다."
