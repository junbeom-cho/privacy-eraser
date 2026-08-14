package kr.co.promptech.privacy_eraser.migration.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingType;

/**
 * 도메인이 정한 마스킹 정책을 Oracle SQL 식으로 옮깁니다.
 * <p>
 * 이 식은 {@code INSERT ... SELECT} 의 SELECT 목록에 들어갑니다. 데이터를 애플리케이션으로
 * 꺼내지 않고 DB 안에서 처리하기 위해서입니다.
 * <p>
 * 규칙은 {@link MaskingPolicy#mask(String)} 과 같아야 합니다. 검수 화면의 미리보기와
 * 실제 이관 결과가 갈리면 안 됩니다.
 */
final class OracleMaskExpression {

	private OracleMaskExpression() {
	}

	/**
	 * @param policy null 이면 마스킹하지 않고 컬럼을 그대로 읽습니다.
	 * @param salt   해시에 붙일 솔트. 이관마다 새로 만들고 보관하지 않습니다
	 */
	static String of(String columnName, MaskingPolicy policy, String salt) {
		String column = quote(columnName);
		if (policy == null) {
			return column;
		}
		// 마스킹 결과는 '*' 가 섞인 문자열이라 숫자·날짜로 되돌릴 수 없습니다. 문자로 다룹니다.
		String text = "TO_CHAR(%s)".formatted(column);

		if (policy.type() == MaskingType.HASH) {
			return hash(text, salt);
		}
		if (policy.type() == MaskingType.FIXED) {
			return fixed(column, policy.fixedValue());
		}
		int length = policy.length();

		String whenShort = "RPAD('*', LENGTH(%s), '*')".formatted(text);
		String otherwise = policy.direction() == MaskingDirection.FROM_START
				? "RPAD('*', %d, '*') || SUBSTR(%s, %d)".formatted(length, text, length + 1)
				: "SUBSTR(%s, 1, LENGTH(%s) - %d) || RPAD('*', %d, '*')".formatted(text, text, length, length);

		return """
				CASE WHEN %s IS NULL THEN NULL \
				WHEN LENGTH(%s) <= %d THEN %s \
				ELSE %s END""".formatted(column, text, length, whenShort, otherwise);
	}

	/**
	 * 값 전체를 SHA-256 으로 바꿉니다. 같은 값은 같은 해시, 다른 값은 다른 해시라 고유성이 유지됩니다.
	 * <p>
	 * {@code STANDARD_HASH} 는 12c 부터의 <b>내장 SQL 함수</b>라 권한이 필요 없습니다.
	 * {@code DBMS_CRYPTO} 는 EXECUTE 권한이 따로 필요해 받은 DB 에서 기대할 수 없습니다.
	 * <p>
	 * 솔트 없이 쓰면 주민등록번호처럼 경우의 수가 적은 값은 전수 대입으로 뚫립니다.
	 */
	private static String hash(String text, String salt) {
		if (salt == null || salt.isBlank()) {
			throw new IllegalArgumentException("해시에는 솔트가 필요합니다.");
		}
		return "LOWER(RAWTOHEX(STANDARD_HASH(%s || '%s', 'SHA256')))".formatted(text, salt);
	}

	/**
	 * 모든 값을 지정한 값 하나로 바꿉니다. 형식을 판별할 필요가 없어 식이 리터럴 하나입니다.
	 * <p>
	 * NULL 은 NULL 로 둡니다. 원래 비어 있던 값을 채우면 없던 데이터가 생깁니다.
가	 * <p>
	 * {@code CAST} 로 감싸는 이유는, Oracle 이 문자 리터럴을 {@code CHAR} 로 보기 때문입니다.
	 * 그대로 두면 {@code CREATE TABLE ... AS SELECT} 가 원본의 {@code VARCHAR2} 를
	 * <b>고정 길이 {@code CHAR}</b> 로 바꿔 버립니다. 뒤가 공백으로 채워지고 비교 방식도 달라집니다.
	 * 길이는 글자 수로 셉니다 — 바이트로 세면 한글 값이 잘립니다.
	 */
	private static String fixed(String column, String value) {
		// 값은 사용자가 입력합니다. 따옴표를 이스케이프하지 않으면 SQL 이 깨집니다.
		return "CASE WHEN %s IS NULL THEN NULL ELSE CAST('%s' AS VARCHAR2(%d CHAR)) END"
				.formatted(column, value.replace("'", "''"), value.length());
	}

	/** 식별자를 그대로 이어붙이면 예약어나 대소문자에서 깨집니다. */
	static String quote(String identifier) {
		return '"' + identifier.replace("\"", "") + '"';
	}
}
