-- 이관 대상은 어차피 있어야 하는 값입니다. 선택값으로 두면 NULL 분기가 도메인·화면 전체로 번집니다.
-- 프로젝트를 만들 때부터 받습니다.
ALTER TABLE project ALTER COLUMN edit_url SET NOT NULL;
ALTER TABLE project ALTER COLUMN edit_username SET NOT NULL;
ALTER TABLE project ALTER COLUMN edit_password SET NOT NULL;
ALTER TABLE project ALTER COLUMN edit_schema SET NOT NULL;
