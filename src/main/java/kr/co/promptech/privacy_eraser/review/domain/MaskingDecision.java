package kr.co.promptech.privacy_eraser.review.domain;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;

/**
 * 컬럼 하나에 대한 판정 결과입니다. 값 객체라 record 입니다.
 *
 * @param matchedKeyword 걸린 키워드. 판정 근거로 화면에 보여줍니다. 사용자 지정·미매칭이면 null
 */
public record MaskingDecision(boolean masked, MaskingPolicy policy, DecisionSource source, String matchedKeyword) {

	public MaskingDecision {
		if (source == null) {
			throw new IllegalArgumentException("판정 근거가 필요합니다.");
		}
		if (masked && policy == null) {
			throw new IllegalArgumentException("마스킹 대상에는 정책이 필요합니다.");
		}
		if (!masked && policy != null) {
			throw new IllegalArgumentException("마스킹 대상이 아니면 정책을 둘 수 없습니다.");
		}
	}

	public static MaskingDecision notMasked() {
		return new MaskingDecision(false, null, DecisionSource.NO_MATCH, null);
	}

	public static MaskingDecision byDoKeyword(String keyword, MaskingPolicy policy) {
		return new MaskingDecision(true, policy, DecisionSource.DO_KEYWORD, keyword);
	}

	public static MaskingDecision byUndoKeyword(String keyword) {
		return new MaskingDecision(false, null, DecisionSource.UNDO_KEYWORD, keyword);
	}

	public static MaskingDecision byUser(boolean masked, MaskingPolicy policy) {
		return new MaskingDecision(masked, masked ? policy : null, DecisionSource.USER, null);
	}
}
