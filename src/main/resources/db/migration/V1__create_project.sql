CREATE SEQUENCE project_seq START 1 INCREMENT 1;

CREATE TABLE project (
    project_id    BIGINT       NOT NULL,
    name          VARCHAR(100) NOT NULL,
    raw_url       VARCHAR(500) NOT NULL,
    raw_username  VARCHAR(128) NOT NULL,
    -- 접속 비밀번호는 CredentialCipher(AES-GCM)로 암호화한 base64. 평문 저장 금지.
    raw_password  VARCHAR(1000) NOT NULL,
    raw_schema    VARCHAR(128) NOT NULL,
    edit_url      VARCHAR(500) NOT NULL,
    edit_username VARCHAR(128) NOT NULL,
    edit_password VARCHAR(1000) NOT NULL,
    edit_schema   VARCHAR(128) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_project PRIMARY KEY (project_id),
    CONSTRAINT uk_project_name UNIQUE (name)
);
