package kr.co.promptech.privacy_eraser.review.domain;

/**
 * 마스킹 여부를 무엇이 정했는지입니다. 검수 화면에서 판정 근거로 보여줍니다.
 */
public enum DecisionSource {

	/** 사용자가 직접 지정했습니다. 무엇보다 우선합니다. */
	USER,

	/** Undo 키워드에 걸려 제외했습니다. */
	UNDO_KEYWORD,

	/** Do 키워드에 걸려 대상이 되었습니다. */
	DO_KEYWORD,

	/** 어느 키워드에도 걸리지 않았습니다. 기본값은 비대상입니다. */
	NO_MATCH
}
