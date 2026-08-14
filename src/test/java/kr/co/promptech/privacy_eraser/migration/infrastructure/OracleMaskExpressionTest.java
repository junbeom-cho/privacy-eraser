package kr.co.promptech.privacy_eraser.migration.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 도메인이 정한 정책("뒤 4자리")을 Oracle SQL 식으로 옮깁니다.
 * 데이터를 애플리케이션으로 꺼내지 않고 DB 안에서 처리하기 위한 번역입니다.
 */
class OracleMaskExpressionTest {

	@Test
	void 마스킹_대상이_아니면_컬럼을_그대로_읽는다() {
		assertThat(OracleMaskExpression.of("SALARY", null, null)).isEqualTo("\"SALARY\"");
	}

	@Test
	void 뒤에서부터_가리는_식을_만든다() {
		String sql = OracleMaskExpression.of("PHONE_NUMBER", MaskingPolicy.partial(MaskingDirection.FROM_END, 4), null);

		// 타입에 상관없이 같은 식이 되도록 컬럼을 TO_CHAR 로 감쌉니다.
		assertThat(sql)
				.contains("SUBSTR(TO_CHAR(\"PHONE_NUMBER\"), 1, LENGTH(TO_CHAR(\"PHONE_NUMBER\")) - 4)")
				.contains("RPAD('*', 4, '*')");
	}

	@Test
	void 앞에서부터_가리는_식을_만든다() {
		String sql = OracleMaskExpression.of("FIRST_NAME", MaskingPolicy.partial(MaskingDirection.FROM_START, 1), null);

		assertThat(sql)
				.contains("RPAD('*', 1, '*')")
				.contains("SUBSTR(TO_CHAR(\"FIRST_NAME\"), 2)");
	}

	@Test
	void NULL_은_NULL_로_남긴다() {
		String sql = OracleMaskExpression.of("EMAIL", MaskingPolicy.partial(MaskingDirection.FROM_END, 4), null);

		assertThat(sql).contains("WHEN \"EMAIL\" IS NULL THEN NULL");
	}

	@Test
	void 값이_정책보다_짧으면_전체를_가린다() {
		// 도메인의 mask() 와 같은 규칙이어야 합니다. 미리보기와 실제 결과가 갈리면 안 됩니다.
		String sql = OracleMaskExpression.of("EMAIL", MaskingPolicy.partial(MaskingDirection.FROM_END, 6), null);

		assertThat(sql)
				.contains("LENGTH(TO_CHAR(\"EMAIL\")) <= 6")
				.contains("RPAD('*', LENGTH(TO_CHAR(\"EMAIL\")), '*')");
	}

	@Test
	void 숫자_날짜도_문자로_바꿔_가린다() {
		// 마스킹 결과는 '*' 가 섞인 문자열이라 원래 타입으로 되돌릴 수 없습니다.
		String sql = OracleMaskExpression.of("SALARY", MaskingPolicy.partial(MaskingDirection.FROM_END, 2), null);

		assertThat(sql).contains("TO_CHAR(\"SALARY\")");
	}

	@Test
	void 컬럼명은_큰따옴표로_감싼다() {
		// 식별자를 그대로 이어붙이면 예약어나 대소문자에서 깨집니다.
		assertThat(OracleMaskExpression.of("COMMENT", null, null)).isEqualTo("\"COMMENT\"");
	}

	// ===== 해시 =====

	private static final String SALT = "a3f9c1";

	@Test
	void 해시는_솔트를_붙여_SHA256_으로_바꾼다() {
		String sql = OracleMaskExpression.of("BMNO", MaskingPolicy.hash(), SALT);

		assertThat(sql).isEqualTo(
				"LOWER(RAWTOHEX(STANDARD_HASH(TO_CHAR(\"BMNO\") || 'a3f9c1', 'SHA256')))");
	}

	@Test
	void 해시는_권한이_필요한_함수를_쓰지_않는다() {
		// DBMS_CRYPTO 는 EXECUTE 권한이 따로 필요합니다. 받은 DB 에서 기대할 수 없습니다.
		assertThat(OracleMaskExpression.of("BMNO", MaskingPolicy.hash(), SALT))
				.doesNotContain("DBMS_CRYPTO");
	}

	@Test
	void 솔트가_없으면_해시를_만들_수_없다() {
		// 솔트 없는 SHA256 은 주민등록번호 정도면 전수 대입으로 뚫립니다.
		assertThatThrownBy(() -> OracleMaskExpression.of("BMNO", MaskingPolicy.hash(), null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("솔트");
	}

	@Test
	void 부분_마스킹에는_솔트가_필요_없다() {
		assertThat(OracleMaskExpression.of("PHONE", MaskingPolicy.partial(MaskingDirection.FROM_END, 4), null))
				.contains("SUBSTR");
	}

	// ===== 고정값 =====

	@Test
	void 고정값은_NULL_만_빼고_전부_같은_값이_된다() {
		String sql = OracleMaskExpression.of("TELNO", MaskingPolicy.fixed("01011111111"), null);

		assertThat(sql).isEqualTo(
				"CASE WHEN \"TELNO\" IS NULL THEN NULL ELSE CAST('01011111111' AS VARCHAR2(11 CHAR)) END");
	}

	@Test
	void 고정값은_VARCHAR2_로_캐스팅한다() {
		// Oracle 은 리터럴을 CHAR 로 봅니다. 그대로 두면 CTAS 가 원본 VARCHAR2 를 고정 길이 CHAR 로
		// 바꿔 뒤를 공백으로 채웁니다.
		assertThat(OracleMaskExpression.of("TELNO", MaskingPolicy.fixed("010"), null))
				.contains("CAST('010' AS VARCHAR2(3 CHAR))");
	}

	@Test
	void 고정값_길이는_바이트가_아니라_글자로_센다() {
		// 한글은 UTF-8 에서 세 바이트라 바이트로 세면 VARCHAR2(2) 가 되어 잘립니다.
		assertThat(OracleMaskExpression.of("NM", MaskingPolicy.fixed("홍길동"), null))
				.contains("VARCHAR2(3 CHAR)");
	}

	@Test
	void 고정값의_따옴표는_이스케이프한다() {
		// 값은 사용자가 입력합니다. 그대로 이어붙이면 SQL 이 깨집니다.
		assertThat(OracleMaskExpression.of("NM", MaskingPolicy.fixed("O'Brien"), null))
				.contains("'O''Brien'");
	}

	@Test
	void 이스케이프한_따옴표는_길이에_세지_않는다() {
		// SQL 에서는 두 글자지만 실제 값은 한 글자입니다.
		assertThat(OracleMaskExpression.of("NM", MaskingPolicy.fixed("O'Brien"), null))
				.contains("VARCHAR2(7 CHAR)");
	}

	@Test
	void 고정값에는_솔트가_필요_없다() {
		assertThat(OracleMaskExpression.of("TELNO", MaskingPolicy.fixed("010"), null))
				.doesNotContain("STANDARD_HASH");
	}
}
