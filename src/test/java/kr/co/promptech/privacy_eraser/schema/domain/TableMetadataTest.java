package kr.co.promptech.privacy_eraser.schema.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableMetadataTest {

	private static final ColumnMetadata EMAIL = ColumnMetadata.character("EMAIL", "VARCHAR2", 25, true);

	@Test
	void 테이블을_만든다() {
		TableMetadata table = new TableMetadata("EMPLOYEES", List.of(EMAIL));

		assertThat(table.name()).isEqualTo("EMPLOYEES");
		assertThat(table.columns()).containsExactly(EMAIL);
	}

	@Test
	void 테이블명이_비어있으면_만들_수_없다() {
		assertThatThrownBy(() -> new TableMetadata(" ", List.of(EMAIL)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TableMetadata(null, List.of(EMAIL)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 컬럼이_없어도_만들_수_있다() {
		// 조회 시점에 권한 문제로 컬럼이 안 보일 수 있습니다. 그것도 사실이므로 거부하지 않습니다.
		assertThat(new TableMetadata("EMPTY", null).columns()).isEmpty();
		assertThat(new TableMetadata("EMPTY", List.of()).columns()).isEmpty();
	}

	@Test
	void 넘겨받은_목록을_나중에_바꿔도_영향받지_않는다() {
		List<ColumnMetadata> mutable = new ArrayList<>(List.of(EMAIL));
		TableMetadata table = new TableMetadata("EMPLOYEES", mutable);

		mutable.clear();

		assertThat(table.columns()).containsExactly(EMAIL);
	}

	@Test
	void 컬럼_목록을_바꾸려_하면_거부한다() {
		TableMetadata table = new TableMetadata("EMPLOYEES", List.of(EMAIL));

		assertThatThrownBy(() -> table.columns().add(EMAIL))
				.isInstanceOf(UnsupportedOperationException.class);
	}
}
