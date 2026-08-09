package kr.co.promptech.privacy_eraser.migration.domain;

/**
 * 복제하는 제약조건 종류입니다. Oracle 은 NOT NULL 도 CHECK 로 저장하지만,
 * 그것은 테이블을 만들 때 이미 따라오므로 여기서 다루지 않습니다.
 */
public enum ConstraintType {

	PRIMARY_KEY,
	UNIQUE,
	CHECK,
	/** 참조 대상이 모두 적재된 뒤에 걸어야 합니다. */
	FOREIGN_KEY
}
