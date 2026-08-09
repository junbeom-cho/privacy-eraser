package kr.co.promptech.privacy_eraser.keyword.domain;

/**
 * 마스킹 정책. 값 객체라 record 입니다.
 *
 * @param length 가릴 문자 수. 실제 값이 이보다 짧을 때의 처리는 프로젝트 설정으로 따로 정합니다.
 */
public record MaskingPolicy(MaskingDirection direction, int length) {

	public MaskingPolicy {
		if (direction == null) {
			throw new IllegalArgumentException("마스킹 방향이 필요합니다.");
		}
		if (length < 1) {
			throw new IllegalArgumentException("마스킹 개수는 1개 이상이어야 합니다.");
		}
	}
}
