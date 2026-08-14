package kr.co.promptech.privacy_eraser.keyword.domain;

/**
 * 마스킹 정책. 값 객체라 record 입니다.
 * <p>
 * 방식이 셋이고 쓰는 칸이 다릅니다.
 *
 * <table>
 * <caption>방식별로 채우는 칸</caption>
 * <tr><th>방식</th><th>direction</th><th>length</th><th>fixedValue</th></tr>
 * <tr><td>{@link MaskingType#PARTIAL}</td><td>필요</td><td>필요</td><td>없음</td></tr>
 * <tr><td>{@link MaskingType#HASH}</td><td>없음</td><td>없음</td><td>없음</td></tr>
 * <tr><td>{@link MaskingType#FIXED}</td><td>없음</td><td>없음</td><td>필요</td></tr>
 * </table>
 *
 * @param direction  부분 마스킹의 방향
 * @param length     가릴 문자 수. 실제 값이 이보다 짧을 때의 처리는 프로젝트 설정으로 따로 정합니다
 * @param fixedValue 고정값으로 바꿀 값
 */
public record MaskingPolicy(MaskingType type, MaskingDirection direction, Integer length, String fixedValue) {

	private static final char MASK = '*';

	public MaskingPolicy {
		if (type == null) {
			throw new IllegalArgumentException("마스킹 방식이 필요합니다.");
		}
		switch (type) {
			case PARTIAL -> {
				if (direction == null) {
					throw new IllegalArgumentException("마스킹 방향이 필요합니다.");
				}
				if (length == null || length < 1) {
					throw new IllegalArgumentException("마스킹 개수는 1개 이상이어야 합니다.");
				}
				fixedValue = null;
			}
			case FIXED -> {
				if (fixedValue == null || fixedValue.isBlank()) {
					throw new IllegalArgumentException("고정값이 필요합니다.");
				}
				// 방향·자릿수에 의미가 없습니다. 값이 남아 있으면 화면과 저장이 어긋납니다.
				direction = null;
				length = null;
			}
			// 해시는 셋 다 쓰지 않습니다.
			case HASH -> {
				direction = null;
				length = null;
				fixedValue = null;
			}
		}
	}

	public static MaskingPolicy partial(MaskingDirection direction, int length) {
		return new MaskingPolicy(MaskingType.PARTIAL, direction, length, null);
	}

	/**
	 * 값 전체를 되돌릴 수 없는 해시로 바꿉니다. 고유성이 유지되어 PK·UNIQUE 컬럼에 쓸 수 있습니다.
	 */
	public static MaskingPolicy hash() {
		return new MaskingPolicy(MaskingType.HASH, null, null, null);
	}

	/**
	 * 모든 값을 지정한 값 하나로 바꿉니다. 형식이 섞인 컬럼에 씁니다.
	 */
	public static MaskingPolicy fixed(String value) {
		return new MaskingPolicy(MaskingType.FIXED, null, null, value);
	}

	/**
	 * 값을 정책대로 바꿉니다.
	 * <p>
	 * 부분 마스킹은 원본 길이를 유지하고, 값이 정책보다 짧으면 <b>전체를 가립니다.</b>
	 * 남기는 쪽이 아니라 가리는 쪽으로 기웁니다 — 개인정보 도구에서는 덜 가리는 실수가 더 위험합니다.
	 *
	 * @return NULL 은 어느 방식이든 그대로 NULL 입니다. <b>해시는 미리 계산할 수 없어 null 입니다</b> —
	 *         솔트가 이관 시점에 정해지기 때문입니다.
	 */
	public String mask(String value) {
		if (type == MaskingType.HASH) {
			return null;
		}
		if (value == null || value.isEmpty()) {
			return value;
		}
		if (type == MaskingType.FIXED) {
			return fixedValue;
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
	 * 부분 마스킹에만 해당합니다. 해시는 항상 같은 길이가 나오고, 고정값은 원본 길이를 보지 않습니다.
	 */
	public boolean masksEntireValue(String value) {
		return type == MaskingType.PARTIAL && value != null && !value.isEmpty() && value.length() <= length;
	}

	/**
	 * 결과가 컬럼에 들어가지 못할 만큼 긴지 여부입니다.
	 * <p>
	 * 고정값만 해당합니다. 컬럼보다 길면 적재 중 {@code ORA-12899} 로 실패하므로 미리 막아야 합니다.
	 * 부분 마스킹은 원본 길이를 유지하고 해시는 컬럼을 문자형으로 새로 만듭니다.
	 *
	 * @param columnLength 컬럼이 담을 수 있는 글자 수. 알 수 없으면 null
	 */
	public boolean exceeds(Integer columnLength) {
		return type == MaskingType.FIXED && columnLength != null && fixedValue.length() > columnLength;
	}

	/**
	 * 서로 다른 값이 같은 결과가 될 수 있는지 여부입니다.
	 * <p>
	 * 겹칠 수 있으면 PK·UNIQUE 컬럼에 쓸 수 없습니다. 부분 마스킹은 겹치는 것이 목적이고,
	 * 고정값은 아예 전부 같아집니다. 해시만 겹치지 않습니다.
	 */
	public boolean mayCollide() {
		return type != MaskingType.HASH;
	}
}
