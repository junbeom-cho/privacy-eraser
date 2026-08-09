-- 이관 실행 이력입니다. 수십 초~몇 분이 걸려 비동기로 돌리므로 상태를 따로 조회합니다.
CREATE SEQUENCE migration_run_seq START 1 INCREMENT 1;

CREATE TABLE migration_run (
    run_id           BIGINT       NOT NULL,
    project_id       BIGINT       NOT NULL,
    status           VARCHAR(12)  NOT NULL,
    total_tables     INTEGER      NOT NULL DEFAULT 0,
    completed_tables INTEGER      NOT NULL DEFAULT 0,
    current_table    VARCHAR(128),
    message          VARCHAR(2000),
    started_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ,
    CONSTRAINT pk_migration_run PRIMARY KEY (run_id),
    CONSTRAINT fk_migration_run_project FOREIGN KEY (project_id) REFERENCES project (project_id) ON DELETE CASCADE,
    CONSTRAINT ck_migration_run_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX ix_migration_run_project ON migration_run (project_id, started_at DESC);

COMMENT ON TABLE migration_run IS '이관 실행';

COMMENT ON COLUMN migration_run.run_id IS '실행 ID';
COMMENT ON COLUMN migration_run.project_id IS '프로젝트 ID';
COMMENT ON COLUMN migration_run.status IS '실행 상태';
COMMENT ON COLUMN migration_run.total_tables IS '전체 테이블 수';
COMMENT ON COLUMN migration_run.completed_tables IS '완료 테이블 수';
COMMENT ON COLUMN migration_run.current_table IS '현재 테이블';
COMMENT ON COLUMN migration_run.message IS '실패 사유';
COMMENT ON COLUMN migration_run.started_at IS '시작일시';
COMMENT ON COLUMN migration_run.finished_at IS '종료일시';
