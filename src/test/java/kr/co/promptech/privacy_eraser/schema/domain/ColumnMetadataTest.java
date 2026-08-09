package kr.co.promptech.privacy_eraser.schema.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColumnMetadataTest {

	private static ColumnMetadata varchar(String name) {
		return ColumnMetadata.character(name, "VARCHAR2", 100, true);
	}

	// ===== 토큰 분리 =====

	@Test
	void 컬럼명을_언더스코어로_분리한다() {
		assertThat(varchar("T_usr_mstr").tokens()).containsExactly("t", "usr", "mstr");
	}

	@Test
	void 토큰은_소문자로_정규화한다() {
		assertThat(varchar("PHONE_NUMBER").tokens()).containsExactly("phone", "number");
	}

	@Test
	void 언더스코어가_없으면_토큰이_하나다() {
		assertThat(varchar("SALARY").tokens()).containsExactly("salary");
	}

	@Test
	void 연속된_언더스코어는_빈_토큰을_만들지_않는다() {
		assertThat(varchar("A__B_").tokens()).containsExactly("a", "b");
	}

	@Test
	void 컬럼명이_비어있으면_만들_수_없다() {
		assertThatThrownBy(() -> varchar(" "))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> varchar(null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 데이터_타입이_비어있으면_만들_수_없다() {
		assertThatThrownBy(() -> ColumnMetadata.character("EMAIL", " ", 25, true))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ColumnMetadata.plain("EMAIL", null, true))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ===== 타입 표기 =====
	// Oracle 의 DATA_LENGTH 는 내부 저장 바이트라 그대로 보여주면 안 됩니다.
	// 검수 화면에서 마스킹 자릿수를 정할 때 이 값을 보고 판단하기 때문입니다.

	@Test
	void 문자형은_글자수를_보여준다() {
		assertThat(ColumnMetadata.character("EMAIL", "VARCHAR2", 25, true).displayType())
				.isEqualTo("VARCHAR2(25)");
	}

	@Test
	void 숫자형은_정밀도와_소수자리를_보여준다() {
		assertThat(ColumnMetadata.number("SALARY", 8, 2, true).displayType()).isEqualTo("NUMBER(8,2)");
	}

	@Test
	void 소수자리가_0이면_정밀도만_보여준다() {
		assertThat(ColumnMetadata.number("EMPLOYEE_ID", 6, 0, false).displayType()).isEqualTo("NUMBER(6)");
	}

	@Test
	void 정밀도가_없는_숫자형은_타입만_보여준다() {
		assertThat(ColumnMetadata.number("QTY", null, null, true).displayType()).isEqualTo("NUMBER");
	}

	@Test
	void 날짜형은_길이를_붙이지_않는다() {
		assertThat(ColumnMetadata.plain("HIRE_DATE", "DATE", false).displayType()).isEqualTo("DATE");
	}

	// ===== 마스킹 가능 길이 =====

	@Test
	void 문자형만_최대_길이를_안다() {
		assertThat(ColumnMetadata.character("EMAIL", "VARCHAR2", 25, true).maxLength()).isEqualTo(25);
		assertThat(ColumnMetadata.plain("HIRE_DATE", "DATE", false).maxLength()).isNull();
	}

	@Test
	void 키가_없으면_빈_집합이다() {
		assertThat(ColumnMetadata.character("NAME", "VARCHAR2", 50, true).keys()).isEmpty();
	}

	@Test
	void PK_와_UNIQUE_는_값이_겹치면_안_되는_컬럼이다() {
		ColumnMetadata pk = ColumnMetadata.number("ID", 10, 0, false).withKeys(Set.of(ColumnKey.PRIMARY_KEY));
		ColumnMetadata unique = ColumnMetadata.character("EMAIL", "VARCHAR2", 25, false)
				.withKeys(Set.of(ColumnKey.UNIQUE));

		assertThat(pk.requiresUniqueValues()).isTrue();
		assertThat(unique.requiresUniqueValues()).isTrue();
	}

	@Test
	void FK_는_값이_겹쳐도_되므로_마스킹_충돌_대상이_아니다() {
		ColumnMetadata fk = ColumnMetadata.number("DEPT_ID", 4, 0, true).withKeys(Set.of(ColumnKey.FOREIGN_KEY));

		assertThat(fk.keys()).containsExactly(ColumnKey.FOREIGN_KEY);
		assertThat(fk.requiresUniqueValues()).isFalse();
	}

	@Test
	void 한_컬럼에_키가_여럿_걸릴_수_있다() {
		ColumnMetadata both = ColumnMetadata.number("DEPT_ID", 4, 0, false)
				.withKeys(Set.of(ColumnKey.PRIMARY_KEY, ColumnKey.FOREIGN_KEY));

		assertThat(both.keys()).containsExactlyInAnyOrder(ColumnKey.PRIMARY_KEY, ColumnKey.FOREIGN_KEY);
		assertThat(both.requiresUniqueValues()).isTrue();
	}

	@Test
	void 키를_붙여도_나머지_정보는_그대로다() {
		ColumnMetadata column = ColumnMetadata.character("EMAIL", "VARCHAR2", 25, false)
				.withKeys(Set.of(ColumnKey.UNIQUE));

		assertThat(column.displayType()).isEqualTo("VARCHAR2(25)");
		assertThat(column.nullable()).isFalse();
	}
}
