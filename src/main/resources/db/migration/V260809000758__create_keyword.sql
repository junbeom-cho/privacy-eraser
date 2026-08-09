CREATE SEQUENCE keyword_seq START 1 INCREMENT 1;

CREATE TABLE keyword (
    keyword_id     BIGINT       NOT NULL,
    project_id     BIGINT       NOT NULL,
    word           VARCHAR(100) NOT NULL,
    keyword_type   VARCHAR(10)  NOT NULL,
    -- UNDO 는 제외가 전부라 정책이 없습니다.
    mask_direction VARCHAR(10),
    mask_length    INTEGER,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_keyword PRIMARY KEY (keyword_id),
    CONSTRAINT uk_keyword_project_word UNIQUE (project_id, word),
    -- 프로젝트를 지우면 키워드도 함께 사라집니다.
    CONSTRAINT fk_keyword_project FOREIGN KEY (project_id) REFERENCES project (project_id) ON DELETE CASCADE,
    CONSTRAINT ck_keyword_type CHECK (keyword_type IN ('DO', 'UNDO')),
    -- DO 는 정책이 반드시 있고, UNDO 는 반드시 없습니다. 도메인 규칙을 DB 에서도 지킵니다.
    CONSTRAINT ck_keyword_policy CHECK (
        (keyword_type = 'DO' AND mask_direction IS NOT NULL AND mask_length >= 1)
        OR (keyword_type = 'UNDO' AND mask_direction IS NULL AND mask_length IS NULL)
    )
);

COMMENT ON TABLE keyword IS '비식별화 키워드';

COMMENT ON COLUMN keyword.keyword_id IS '키워드 ID';
COMMENT ON COLUMN keyword.project_id IS '프로젝트 ID';
COMMENT ON COLUMN keyword.word IS '키워드';
COMMENT ON COLUMN keyword.keyword_type IS '키워드 종류';
COMMENT ON COLUMN keyword.mask_direction IS '마스킹 방향';
COMMENT ON COLUMN keyword.mask_length IS '마스킹 개수';
COMMENT ON COLUMN keyword.created_at IS '생성일시';
