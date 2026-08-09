-- 이관 후 통계입니다. 표본 1행으로는 몇 건이 통째로 가려졌는지 알 수 없어 전수를 셉니다.
CREATE SEQUENCE migration_column_stat_seq START 1 INCREMENT 1;

CREATE TABLE migration_column_stat (
    stat_id           BIGINT       NOT NULL,
    run_id            BIGINT       NOT NULL,
    table_name        VARCHAR(128) NOT NULL,
    column_name       VARCHAR(128) NOT NULL,
    total_rows        BIGINT       NOT NULL,
    fully_masked_rows BIGINT       NOT NULL,
    CONSTRAINT pk_migration_column_stat PRIMARY KEY (stat_id),
    CONSTRAINT fk_migration_column_stat_run FOREIGN KEY (run_id) REFERENCES migration_run (run_id) ON DELETE CASCADE
);

CREATE INDEX ix_migration_column_stat_run ON migration_column_stat (run_id);

COMMENT ON TABLE migration_column_stat IS '이관 컬럼 통계';

COMMENT ON COLUMN migration_column_stat.stat_id IS '통계 ID';
COMMENT ON COLUMN migration_column_stat.run_id IS '실행 ID';
COMMENT ON COLUMN migration_column_stat.table_name IS '테이블명';
COMMENT ON COLUMN migration_column_stat.column_name IS '컬럼명';
COMMENT ON COLUMN migration_column_stat.total_rows IS '전체 행수';
COMMENT ON COLUMN migration_column_stat.fully_masked_rows IS '전체 마스킹 행수';
