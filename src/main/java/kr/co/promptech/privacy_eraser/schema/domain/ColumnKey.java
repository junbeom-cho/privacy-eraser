package kr.co.promptech.privacy_eraser.schema.domain;

/**
 * 컬럼에 걸린 키입니다. 검수할 때 이걸 알아야 합니다.
 * <p>
 * PK·UNIQUE 컬럼을 마스킹하면 값이 서로 겹쳐 이관할 때 제약조건을 걸 수 없습니다.
 * 이관 시작 전에 막기는 하지만, 검수 화면에서 미리 보이는 편이 낫습니다.
 */
public enum ColumnKey {

	PRIMARY_KEY, UNIQUE, FOREIGN_KEY
}
