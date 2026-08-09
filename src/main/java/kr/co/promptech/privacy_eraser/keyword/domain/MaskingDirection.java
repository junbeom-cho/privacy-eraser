package kr.co.promptech.privacy_eraser.keyword.domain;

/**
 * 값의 어느 쪽부터 가릴지입니다.
 */
public enum MaskingDirection {

	/** 앞에서부터 (홍길동 → **동) */
	FROM_START,

	/** 뒤에서부터 (01012345678 → 0101234****) */
	FROM_END
}
