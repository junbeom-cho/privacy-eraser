package kr.co.promptech.privacy_eraser.migration.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;

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
	 */
	static String of(String columnName, MaskingPolicy policy) {
		String column = quote(columnName);
		if (policy == null) {
			return column;
		}
		// 마스킹 결과는 '*' 가 섞인 문자열이라 숫자·날짜로 되돌릴 수 없습니다. 문자로 다룹니다.
		String text = "TO_CHAR(%s)".formatted(column);
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

	/** 식별자를 그대로 이어붙이면 예약어나 대소문자에서 깨집니다. */
	static String quote(String identifier) {
		return '"' + identifier.replace("\"", "") + '"';
	}
}
