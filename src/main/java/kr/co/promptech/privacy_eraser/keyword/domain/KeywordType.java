package kr.co.promptech.privacy_eraser.keyword.domain;

/**
 * 키워드 종류입니다. 둘 다 걸리면 {@link #UNDO} 가 이깁니다.
 */
public enum KeywordType {

	/** 이 키워드가 걸린 컬럼은 마스킹 대상입니다. 정책이 반드시 있어야 합니다. */
	DO,

	/** 이 키워드가 걸린 컬럼은 마스킹에서 제외합니다. 제외가 전부라 정책이 없습니다. */
	UNDO
}
