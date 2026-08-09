package kr.co.promptech.privacy_eraser.keyword.domain;

/**
 * 마스킹 정책. 값 객체라 record 입니다.
 *
 * @param length 가릴 문자 수. 실제 값이 이보다 짧을 때의 처리는 프로젝트 설정으로 따로 정합니다.
 */
public record MaskingPolicy(MaskingDirection direction, int length) {

	private static final char MASK = '*';

	public MaskingPolicy {
		if (direction == null) {
			throw new IllegalArgumentException("마스킹 방향이 필요합니다.");
		}
		if (length < 1) {
			throw new IllegalArgumentException("마스킹 개수는 1개 이상이어야 합니다.");
		}
	}

	/**
	 * 값을 정책대로 가립니다. 원본 길이는 유지합니다.
	 * <p>
	 * 값이 정책보다 짧으면 <b>전체를 가립니다.</b> 남기는 쪽이 아니라 가리는 쪽으로 기웁니다 —
	 * 개인정보 도구에서는 덜 가리는 실수가 더 위험합니다.
	 *
	 * @return NULL 은 그대로 NULL 입니다.
	 */
	public String mask(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		int maskCount = Math.min(length, value.length());
		String masked = String.valueOf(MASK).repeat(maskCount);
		return direction == MaskingDirection.FROM_START
				? masked + value.substring(maskCount)
				: value.substring(0, value.length() - maskCount) + masked;
	}

	/**
	 * 이 값이 통째로 가려지는지 여부입니다. 값이 정책보다 짧거나 같으면 남는 글자가 없습니다.
	 * 검수 화면에서 미리 알려 사용자가 정책을 조정할 수 있게 합니다.
	 */
	public boolean masksEntireValue(String value) {
		return value != null && !value.isEmpty() && value.length() <= length;
	}
}
