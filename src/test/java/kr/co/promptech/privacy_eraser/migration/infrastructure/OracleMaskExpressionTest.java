package kr.co.promptech.privacy_eraser.migration.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 도메인이 정한 정책("뒤 4자리")을 Oracle SQL 식으로 옮깁니다.
 * 데이터를 애플리케이션으로 꺼내지 않고 DB 안에서 처리하기 위한 번역입니다.
 */
class OracleMaskExpressionTest {

	@Test
	void 마스킹_대상이_아니면_컬럼을_그대로_읽는다() {
		assertThat(OracleMaskExpression.of("SALARY", null)).isEqualTo("\"SALARY\"");
	}

	@Test
	void 뒤에서부터_가리는_식을_만든다() {
		String sql = OracleMaskExpression.of("PHONE_NUMBER", new MaskingPolicy(MaskingDirection.FROM_END, 4));

		// 타입에 상관없이 같은 식이 되도록 컬럼을 TO_CHAR 로 감쌉니다.
		assertThat(sql)
				.contains("SUBSTR(TO_CHAR(\"PHONE_NUMBER\"), 1, LENGTH(TO_CHAR(\"PHONE_NUMBER\")) - 4)")
				.contains("RPAD('*', 4, '*')");
	}

	@Test
	void 앞에서부터_가리는_식을_만든다() {
		String sql = OracleMaskExpression.of("FIRST_NAME", new MaskingPolicy(MaskingDirection.FROM_START, 1));

		assertThat(sql)
				.contains("RPAD('*', 1, '*')")
				.contains("SUBSTR(TO_CHAR(\"FIRST_NAME\"), 2)");
	}

	@Test
	void NULL_은_NULL_로_남긴다() {
		String sql = OracleMaskExpression.of("EMAIL", new MaskingPolicy(MaskingDirection.FROM_END, 4));

		assertThat(sql).contains("WHEN \"EMAIL\" IS NULL THEN NULL");
	}

	@Test
	void 값이_정책보다_짧으면_전체를_가린다() {
		// 도메인의 mask() 와 같은 규칙이어야 합니다. 미리보기와 실제 결과가 갈리면 안 됩니다.
		String sql = OracleMaskExpression.of("EMAIL", new MaskingPolicy(MaskingDirection.FROM_END, 6));

		assertThat(sql)
				.contains("LENGTH(TO_CHAR(\"EMAIL\")) <= 6")
				.contains("RPAD('*', LENGTH(TO_CHAR(\"EMAIL\")), '*')");
	}

	@Test
	void 숫자_날짜도_문자로_바꿔_가린다() {
		// 마스킹 결과는 '*' 가 섞인 문자열이라 원래 타입으로 되돌릴 수 없습니다.
		String sql = OracleMaskExpression.of("SALARY", new MaskingPolicy(MaskingDirection.FROM_END, 2));

		assertThat(sql).contains("TO_CHAR(\"SALARY\")");
	}

	@Test
	void 컬럼명은_큰따옴표로_감싼다() {
		// 식별자를 그대로 이어붙이면 예약어나 대소문자에서 깨집니다.
		assertThat(OracleMaskExpression.of("COMMENT", null)).isEqualTo("\"COMMENT\"");
	}
}
