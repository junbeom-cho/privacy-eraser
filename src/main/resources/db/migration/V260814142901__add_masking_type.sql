-- 비식별화 방식이 둘이 됐습니다. 부분 마스킹은 값이 겹쳐 PK·UNIQUE 컬럼에 쓸 수 없어,
-- 그런 컬럼에는 해시를 씁니다. 해시는 방향·자릿수를 쓰지 않습니다.
ALTER TABLE keyword ADD COLUMN mask_type VARCHAR(10);
ALTER TABLE column_override ADD COLUMN mask_type VARCHAR(10);

-- 기존 정책은 전부 부분 마스킹입니다. 정책이 없는 행(Undo 키워드, 비대상 컬럼)은 그대로 NULL 입니다.
UPDATE keyword SET mask_type = 'PARTIAL' WHERE mask_direction IS NOT NULL;
UPDATE column_override SET mask_type = 'PARTIAL' WHERE mask_direction IS NOT NULL;

-- 정책이 있으면 방식도 있어야 하고, 방식마다 채워야 하는 칸이 다릅니다.
-- 이 검사가 없으면 해시인데 자릿수가 남아 있는 행이 조용히 생깁니다.
ALTER TABLE keyword DROP CONSTRAINT ck_keyword_policy;
ALTER TABLE keyword ADD CONSTRAINT ck_keyword_policy CHECK (
    (keyword_type = 'DO'   AND mask_type = 'PARTIAL' AND mask_direction IS NOT NULL AND mask_length >= 1)
 OR (keyword_type = 'DO'   AND mask_type = 'HASH'    AND mask_direction IS NULL     AND mask_length IS NULL)
 OR (keyword_type = 'UNDO' AND mask_type IS NULL     AND mask_direction IS NULL     AND mask_length IS NULL)
);

ALTER TABLE column_override DROP CONSTRAINT ck_column_override_policy;
ALTER TABLE column_override ADD CONSTRAINT ck_column_override_policy CHECK (
    (masked     AND mask_type = 'PARTIAL' AND mask_direction IS NOT NULL AND mask_length >= 1)
 OR (masked     AND mask_type = 'HASH'    AND mask_direction IS NULL     AND mask_length IS NULL)
 OR (NOT masked AND mask_type IS NULL     AND mask_direction IS NULL     AND mask_length IS NULL)
);

COMMENT ON COLUMN keyword.mask_type IS '마스킹 방식';
COMMENT ON COLUMN column_override.mask_type IS '마스킹 방식';
