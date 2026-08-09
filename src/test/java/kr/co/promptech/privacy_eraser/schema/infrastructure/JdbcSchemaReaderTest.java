package kr.co.promptech.privacy_eraser.schema.infrastructure;

import kr.co.promptech.privacy_eraser.schema.domain.ColumnKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 접속이 필요한 부분은 여기서 확인할 수 없습니다. Oracle 이 주는 한 글자 코드를 옮기는 것만 봅니다.
 */
class JdbcSchemaReaderTest {

	@Test
	void 제약조건_코드를_키로_옮긴다() {
		assertThat(JdbcSchemaReader.toColumnKey("P")).isEqualTo(ColumnKey.PRIMARY_KEY);
		assertThat(JdbcSchemaReader.toColumnKey("U")).isEqualTo(ColumnKey.UNIQUE);
		assertThat(JdbcSchemaReader.toColumnKey("R")).isEqualTo(ColumnKey.FOREIGN_KEY);
	}

	@Test
	void 테이블과_컬럼을_함께_묶어야_이름이_같은_컬럼이_섞이지_않는다() {
		assertThat(JdbcSchemaReader.keyOf("EMPLOYEES", "DEPARTMENT_ID"))
				.isNotEqualTo(JdbcSchemaReader.keyOf("DEPARTMENTS", "DEPARTMENT_ID"));
	}
}
