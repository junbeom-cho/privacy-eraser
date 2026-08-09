-- 검수 화면에서 사용자가 직접 정한 컬럼 결정입니다. 키워드 판정보다 우선합니다.
-- 원본에 컬럼이 추가되어 다시 스캔해도 이 행들은 남습니다.
CREATE SEQUENCE column_override_seq START 1 INCREMENT 1;

CREATE TABLE column_override (
    override_id    BIGINT       NOT NULL,
    project_id     BIGINT       NOT NULL,
    table_name     VARCHAR(128) NOT NULL,
    column_name    VARCHAR(128) NOT NULL,
    masked         BOOLEAN      NOT NULL,
    -- 마스킹하지 않기로 했으면 정책이 없습니다.
    mask_direction VARCHAR(10),
    mask_length    INTEGER,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_column_override PRIMARY KEY (override_id),
    CONSTRAINT uk_column_override UNIQUE (project_id, table_name, column_name),
    CONSTRAINT fk_column_override_project FOREIGN KEY (project_id) REFERENCES project (project_id) ON DELETE CASCADE,
    CONSTRAINT ck_column_override_policy CHECK (
        (masked AND mask_direction IS NOT NULL AND mask_length >= 1)
        OR (NOT masked AND mask_direction IS NULL AND mask_length IS NULL)
    )
);

COMMENT ON TABLE column_override IS '컬럼 검수 결과';

COMMENT ON COLUMN column_override.override_id IS '검수 결과 ID';
COMMENT ON COLUMN column_override.project_id IS '프로젝트 ID';
COMMENT ON COLUMN column_override.table_name IS '테이블명';
COMMENT ON COLUMN column_override.column_name IS '컬럼명';
COMMENT ON COLUMN column_override.masked IS '마스킹 여부';
COMMENT ON COLUMN column_override.mask_direction IS '마스킹 방향';
COMMENT ON COLUMN column_override.mask_length IS '마스킹 개수';
COMMENT ON COLUMN column_override.created_at IS '생성일시';
