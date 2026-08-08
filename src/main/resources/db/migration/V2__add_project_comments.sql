COMMENT ON TABLE project IS '비식별화 프로젝트. raw_schema 에서 읽어 edit_schema 로 이관하는 한 벌의 설정';

COMMENT ON COLUMN project.project_id IS '프로젝트 ID (project_seq)';
COMMENT ON COLUMN project.name IS '프로젝트 이름. 중복 불가';

COMMENT ON COLUMN project.raw_url IS '원본 DB JDBC URL. 비식별화 대상이며 읽기 전용으로만 접근한다';
COMMENT ON COLUMN project.raw_username IS '원본 DB 접속 계정';
COMMENT ON COLUMN project.raw_password IS '원본 DB 접속 비밀번호. AES-GCM 암호문(base64), 평문 저장 금지';
COMMENT ON COLUMN project.raw_schema IS '원본 스키마명. 대문자로 정규화되어 저장된다';

COMMENT ON COLUMN project.edit_url IS '이관 대상 DB JDBC URL. 마스킹 결과를 여기에 쓴다';
COMMENT ON COLUMN project.edit_username IS '이관 대상 DB 접속 계정';
COMMENT ON COLUMN project.edit_password IS '이관 대상 DB 접속 비밀번호. AES-GCM 암호문(base64), 평문 저장 금지';
COMMENT ON COLUMN project.edit_schema IS '이관 대상 스키마명. raw_schema 와 같으면 원본을 덮어쓰므로 거부된다';

COMMENT ON COLUMN project.created_at IS '생성 시각';
