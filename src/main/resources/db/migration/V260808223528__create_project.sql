CREATE SEQUENCE project_seq START 1 INCREMENT 1;

CREATE TABLE project (
    project_id    BIGINT        NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    raw_url       VARCHAR(500)  NOT NULL,
    raw_username  VARCHAR(128)  NOT NULL,
    raw_password  VARCHAR(1000) NOT NULL,
    raw_schema    VARCHAR(128)  NOT NULL,
    -- 이관 대상은 비식별화를 실행할 때 정하므로 생성 시점에는 비어 있습니다.
    edit_url      VARCHAR(500),
    edit_username VARCHAR(128),
    edit_password VARCHAR(1000),
    edit_schema   VARCHAR(128),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_project PRIMARY KEY (project_id),
    CONSTRAINT uk_project_name UNIQUE (name)
);

COMMENT ON TABLE project IS '비식별화 프로젝트';

COMMENT ON COLUMN project.project_id IS '프로젝트 ID';
COMMENT ON COLUMN project.name IS '프로젝트명';

COMMENT ON COLUMN project.raw_url IS '원본 접속 URL';
COMMENT ON COLUMN project.raw_username IS '원본 접속 계정';
COMMENT ON COLUMN project.raw_password IS '원본 접속 비밀번호';
COMMENT ON COLUMN project.raw_schema IS '원본 스키마명';

COMMENT ON COLUMN project.edit_url IS '이관 대상 접속 URL';
COMMENT ON COLUMN project.edit_username IS '이관 대상 접속 계정';
COMMENT ON COLUMN project.edit_password IS '이관 대상 접속 비밀번호';
COMMENT ON COLUMN project.edit_schema IS '이관 대상 스키마명';

COMMENT ON COLUMN project.created_at IS '생성일시';
