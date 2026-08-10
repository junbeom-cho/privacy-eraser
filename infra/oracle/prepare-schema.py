#!/usr/bin/env python3
"""infra/oracle/sample 의 스키마 SQL 을 도커 Oracle 에 넣을 수 있는 한 파일로 만듭니다.

원본은 CP949 · CRLF 이고, 같은 테이블 정의가 여러 번 들어 있습니다.
**겹치면 위쪽에 있는 것이 최신**이라, 아래쪽 정의를 그대로 실행하면 옛 구조로 덮어씁니다
(각 블록이 DROP TABLE 로 시작하기 때문입니다). 그래서 첫 번째 정의만 남깁니다.

    python3 infra/oracle/prepare-schema.py

결과: infra/oracle/sample/schema_prepared.sql (UTF-8)
"""
import collections
import pathlib
import re
import sys

SAMPLE = pathlib.Path(__file__).resolve().parent / "sample"
OUT = SAMPLE / "schema_prepared.sql"

# 원본 스키마에서 쓰던 테이블스페이스와 롤입니다. 없으면 CREATE TABLE 이 ORA-00959 로 실패합니다.
TABLESPACES = ["TSD_BM01", "TSI_BM01", "TSD_SC01", "TSI_SC01"]
ROLES = ["VMISAPP_RL", "VMISDEV_RL", "VMISBAT_RL"]


# DEFAULT 뒤에 그대로 와도 되는 것들입니다. 나머지 맨이름은 문자열인데 따옴표가 빠진 것입니다.
DEFAULT_KEYWORDS = {"NULL", "SYSDATE", "SYSTIMESTAMP", "CURRENT_DATE", "CURRENT_TIMESTAMP", "USER"}


def read(path):
    """CP949 로 저장돼 있습니다. 한글 컬럼 코멘트가 깨지면 안 됩니다."""
    return path.read_bytes().decode("cp949").replace("\r\n", "\n")


def fix_bare_default(text):
    """원본에 `DEFAULT N` 처럼 따옴표가 빠진 곳이 있어 ORA-00904 로 테이블이 안 만들어집니다."""

    def quote(match):
        token = match.group(1)
        if token.upper() in DEFAULT_KEYWORDS:
            return match.group(0)
        return "DEFAULT '%s'" % token

    return re.sub(r"(?i)\bDEFAULT\s+([A-Za-z][A-Za-z0-9_]*)(?=\s|,|\))", quote, text)


def split_blocks(text):
    """`-- ===` + `-- DBMS TYPE` 헤더가 블록의 시작입니다."""
    chunks = re.split(r"(?m)(?=^-- =+\s*$\n-- DBMS TYPE)", text)
    blocks = []
    for chunk in chunks:
        match = re.search(r"-- TABLE NAME\s*:\s*(\S+)", chunk)
        if match:
            blocks.append((match.group(1).upper(), chunk))
    return blocks


def split_statements(text):
    """따옴표 밖의 `;` 로 자릅니다. 코멘트 본문에 `;` 가 들어갈 수 있습니다."""
    statements, buf, quoted, i = [], [], False, 0
    while i < len(text):
        c = text[i]
        if quoted:
            buf.append(c)
            if c == "'":
                quoted = False
        elif c == "'":
            buf.append(c)
            quoted = True
        elif c == "-" and text[i:i + 2] == "--":
            end = text.find("\n", i)
            i = len(text) if end < 0 else end
            continue
        elif c == ";":
            statements.append("".join(buf).strip())
            buf = []
        else:
            buf.append(c)
        i += 1
    tail = "".join(buf).strip()
    if tail:
        statements.append(tail)
    return [s for s in statements if s]


def name_of(statement):
    """같은 이름을 두 번 만들면 ORA-00955 입니다. 인덱스·제약조건은 이름으로 한 번만 만듭니다."""
    index = re.match(r"(?is)\s*CREATE\s+(?:UNIQUE\s+)?INDEX\s+([A-Z0-9_.\"]+)", statement)
    if index:
        return "INDEX " + index.group(1).upper().replace('"', "")
    constraint = re.search(r"(?is)^\s*ALTER\s+TABLE\s+\S+\s+ADD\s+CONSTRAINT\s+([A-Z0-9_.\"]+)", statement)
    if constraint:
        return "CONSTRAINT " + constraint.group(1).upper().replace('"', "")
    return None


def main():
    files = sorted(SAMPLE.glob("QA_*.sql"))
    if not files:
        sys.exit("infra/oracle/sample 에 QA_*.sql 이 없습니다.")

    kept, dropped_blocks, seen_tables = [], 0, set()
    for path in files:
        for table, body in split_blocks(fix_bare_default(read(path))):
            # CREATE TABLE 이 있는 블록이 '테이블 정의'입니다. 나머지는 인덱스만 추가하는 블록입니다.
            if re.search(r"(?i)\bCREATE\s+TABLE\b", body):
                if table in seen_tables:
                    dropped_blocks += 1   # 아래쪽 = 옛 정의
                    continue
                seen_tables.add(table)
            kept.append(body)

    statements, seen_names, dropped_names = [], set(), 0
    for statement in split_statements("\n".join(kept)):
        key = name_of(statement)
        if key:
            if key in seen_names:
                dropped_names += 1
                continue
            seen_names.add(key)
        statements.append(statement)

    header = [
        "-- prepare-schema.py 가 만든 파일입니다. 직접 고치지 마세요.",
        "-- 겹치는 테이블 정의는 위쪽(최신)만 남겼습니다.",
        "SET DEFINE OFF",
        "SET ECHO OFF",
        "-- 첫 실행에서는 DROP TABLE 이 ORA-00942 로 실패합니다. 정상입니다.",
        "WHENEVER SQLERROR CONTINUE",
    ]
    body = [s + ";" for s in statements]
    OUT.write_text("\n".join(header + body + ["EXIT"]) + "\n", encoding="utf-8")

    kinds = collections.Counter()
    for s in statements:
        kinds[re.sub(r"\s+", " ", s.strip().split("(")[0].upper()[:14]).strip()] += 1
    print("테이블 %d개, 옛 정의 블록 %d개 제외, 중복 이름 %d개 제외" % (
        len(seen_tables), dropped_blocks, dropped_names))
    print("문장 %d개 → %s" % (len(statements), OUT))
    for k, v in kinds.most_common(8):
        print("  %-16s %d" % (k, v))


if __name__ == "__main__":
    main()
