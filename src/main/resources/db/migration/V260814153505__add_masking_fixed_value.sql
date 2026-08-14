-- 형식이 섞인 컬럼(전화번호 등)은 위치 기반 마스킹으로 맞출 수 없어 값 하나로 통일합니다.
ALTER TABLE keyword ADD COLUMN mask_value VARCHAR(200);
ALTER TABLE column_override ADD COLUMN mask_value VARCHAR(200);

-- 방식마다 채워야 하는 칸이 다릅니다. 이 검사가 없으면 해시인데 고정값이 남아 있는 행이 조용히 생깁니다.
ALTER TABLE keyword DROP CONSTRAINT ck_keyword_policy;
ALTER TABLE keyword ADD CONSTRAINT ck_keyword_policy CHECK (
    (keyword_type = 'DO'   AND mask_type = 'PARTIAL' AND mask_direction IS NOT NULL AND mask_length >= 1  AND mask_value IS NULL)
 OR (keyword_type = 'DO'   AND mask_type = 'HASH'    AND mask_direction IS NULL     AND mask_length IS NULL AND mask_value IS NULL)
 OR (keyword_type = 'DO'   AND mask_type = 'FIXED'   AND mask_direction IS NULL     AND mask_length IS NULL AND mask_value IS NOT NULL)
 OR (keyword_type = 'UNDO' AND mask_type IS NULL     AND mask_direction IS NULL     AND mask_length IS NULL AND mask_value IS NULL)
);

ALTER TABLE column_override DROP CONSTRAINT ck_column_override_policy;
ALTER TABLE column_override ADD CONSTRAINT ck_column_override_policy CHECK (
    (masked     AND mask_type = 'PARTIAL' AND mask_direction IS NOT NULL AND mask_length >= 1  AND mask_value IS NULL)
 OR (masked     AND mask_type = 'HASH'    AND mask_direction IS NULL     AND mask_length IS NULL AND mask_value IS NULL)
 OR (masked     AND mask_type = 'FIXED'   AND mask_direction IS NULL     AND mask_length IS NULL AND mask_value IS NOT NULL)
 OR (NOT masked AND mask_type IS NULL     AND mask_direction IS NULL     AND mask_length IS NULL AND mask_value IS NULL)
);

COMMENT ON COLUMN keyword.mask_value IS '고정값';
COMMENT ON COLUMN column_override.mask_value IS '고정값';
