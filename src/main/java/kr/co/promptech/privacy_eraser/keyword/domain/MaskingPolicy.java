package kr.co.promptech.privacy_eraser.keyword.domain;

/**
 * 마스킹 정책. 값 객체라 record 입니다.
 * <p>
 * 방식이 둘입니다. {@link MaskingType#PARTIAL} 은 방향과 자릿수를 쓰고,
 * {@link MaskingType#HASH} 는 둘 다 쓰지 않습니다.
 *
 * @param direction 부분 마스킹의 방향. 해시면 null
 * @param length    가릴 문자 수. 실제 값이 이보다 짧을 때의 처리는 프로젝트 설정으로 따로 정합니다. 해시면 null
 */
public record MaskingPolicy(MaskingType type, MaskingDirection direction, Integer length) {

	private static final char MASK = '*';

	public MaskingPolicy {
		if (type == null) {
			throw new IllegalArgumentException("마스킹 방식이 필요합니다.");
		}
		if (type == MaskingType.PARTIAL) {
			if (direction == null) {
				throw new IllegalArgumentException("마스킹 방향이 필요합니다.");
			}
			if (length == null || length < 1) {
				throw new IllegalArgumentException("마스킹 개수는 1개 이상이어야 합니다.");
			}
		}
		else {
			// 해시에는 방향도 자릿수도 의미가 없습니다. 값이 남아 있으면 화면과 저장이 어긋납니다.
			direction = null;
			length = null;
		}
	}

	public static MaskingPolicy partial(MaskingDirection direction, int length) {
		return new MaskingPolicy(MaskingType.PARTIAL, direction, length);
	}

	/**
	 * 값 전체를 되돌릴 수 없는 해시로 바꿉니다. 고유성이 유지되어 PK·UNIQUE 컬럼에 쓸 수 있습니다.
	 */
	public static MaskingPolicy hash() {
		return new MaskingPolicy(MaskingType.HASH, null, null);
	}

	/**
	 * 값을 정책대로 가립니다. 원본 길이는 유지합니다.
	 * <p>
	 * 값이 정책보다 짧으면 <b>전체를 가립니다.</b> 남기는 쪽이 아니라 가리는 쪽으로 기웁니다 —
	 * 개인정보 도구에서는 덜 가리는 실수가 더 위험합니다.
	 *
	 * @return NULL 은 그대로 NULL 입니다. <b>해시는 미리 계산할 수 없어 null 입니다</b> —
	 *         솔트가 이관 시점에 정해지기 때문입니다.
	 */
	public String mask(String value) {
		if (type == MaskingType.HASH) {
			return null;
		}
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
	 * <p>
	 * 해시는 값 길이와 무관하게 항상 같은 길이가 나오므로 해당하지 않습니다.
	 */
	public boolean masksEntireValue(String value) {
		return type == MaskingType.PARTIAL && value != null && !value.isEmpty() && value.length() <= length;
	}

	/**
	 * 서로 다른 값이 같은 결과가 될 수 있는지 여부입니다.
	 * <p>
	 * 겹칠 수 있으면 PK·UNIQUE 컬럼에 쓸 수 없습니다. 부분 마스킹은 겹치는 것이 목적이고,
	 * 해시는 겹치지 않는 것이 목적입니다.
	 */
	public boolean mayCollide() {
		return type == MaskingType.PARTIAL;
	}
}
