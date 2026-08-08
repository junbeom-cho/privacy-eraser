package kr.co.promptech.privacy_eraser.schema.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 원본 컬럼 한 개의 메타데이터입니다.
 * <p>
 * Oracle 의 {@code DATA_LENGTH} 는 내부 저장 바이트 수라 화면에 그대로 쓰면 안 됩니다
 * ({@code DATE} 가 7, {@code NUMBER} 가 22 로 나옵니다). 문자형은 글자 수를, 숫자형은
 * 정밀도·소수 자리를 따로 받습니다. 검수 화면에서 이 값을 보고 마스킹 자릿수를 정하기 때문입니다.
 *
 * @param charLength 문자형의 글자 수. 문자형이 아니면 null
 * @param precision  숫자형의 전체 자릿수. 숫자형이 아니거나 지정되지 않았으면 null
 * @param scale      숫자형의 소수 자릿수. 숫자형이 아니면 null
 */
public record ColumnMetadata(String name, String dataType, Integer charLength,
		Integer precision, Integer scale, boolean nullable) {

	public ColumnMetadata {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("컬럼명은 비워둘 수 없습니다.");
		}
		if (dataType == null || dataType.isBlank()) {
			throw new IllegalArgumentException("데이터 타입은 비워둘 수 없습니다.");
		}
		name = name.strip();
	}

	public static ColumnMetadata character(String name, String dataType, int charLength, boolean nullable) {
		return new ColumnMetadata(name, dataType, charLength, null, null, nullable);
	}

	public static ColumnMetadata number(String name, Integer precision, Integer scale, boolean nullable) {
		return new ColumnMetadata(name, "NUMBER", null, precision, scale, nullable);
	}

	/** 날짜·LOB 처럼 길이를 붙이지 않는 타입입니다. */
	public static ColumnMetadata plain(String name, String dataType, boolean nullable) {
		return new ColumnMetadata(name, dataType, null, null, null, nullable);
	}

	/**
	 * 컬럼명을 `_` 기준으로 나눈 토큰입니다. 키워드는 이 토큰과 대조합니다.
	 * 대소문자가 섞여 들어오므로 소문자로 맞춥니다 (`T_usr_mstr` → t, usr, mstr).
	 */
	public List<String> tokens() {
		return Arrays.stream(name.toLowerCase(Locale.ROOT).split("_"))
				.filter(token -> !token.isBlank())
				.toList();
	}

	/**
	 * 마스킹할 수 있는 최대 글자 수입니다. 문자형이 아니면 알 수 없어 null 입니다.
	 */
	public Integer maxLength() {
		return charLength;
	}

	public String displayType() {
		if (charLength != null) {
			return "%s(%d)".formatted(dataType, charLength);
		}
		if (precision == null) {
			return dataType;
		}
		return scale != null && scale > 0
				? "%s(%d,%d)".formatted(dataType, precision, scale)
				: "%s(%d)".formatted(dataType, precision);
	}
}
