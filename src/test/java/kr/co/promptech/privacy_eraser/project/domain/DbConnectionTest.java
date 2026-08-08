package kr.co.promptech.privacy_eraser.project.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DbConnectionTest {

	private static DbConnection connection(String url, String schema) {
		return new DbConnection(url, "app", "pw", schema);
	}

	@Test
	void jdbc로_시작하지_않는_url은_거부한다() {
		assertThatThrownBy(() -> connection("oracle://localhost:1521/XE", "RAW"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 사용자명이_비어있으면_거부한다() {
		assertThatThrownBy(() -> new DbConnection("jdbc:oracle:thin:@localhost:1521/XE", " ", "pw", "RAW"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 스키마가_비어있으면_거부한다() {
		assertThatThrownBy(() -> connection("jdbc:oracle:thin:@localhost:1521/XE", ""))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 스키마는_오라클_식별자에_맞춰_대문자로_정규화된다() {
		assertThat(connection("jdbc:oracle:thin:@localhost:1521/XE", " raw_schema ").schema())
				.isEqualTo("RAW_SCHEMA");
	}

	@Test
	void url과_스키마가_모두_같으면_같은_대상이다() {
		DbConnection raw = connection("jdbc:oracle:thin:@localhost:1521/XE", "RAW");
		DbConnection same = new DbConnection("jdbc:oracle:thin:@localhost:1521/XE", "other", "other", "raw");

		assertThat(raw.sameTarget(same)).isTrue();
	}

	@Test
	void 스키마가_다르면_다른_대상이다() {
		DbConnection raw = connection("jdbc:oracle:thin:@localhost:1521/XE", "RAW");
		DbConnection edit = connection("jdbc:oracle:thin:@localhost:1521/XE", "EDIT");

		assertThat(raw.sameTarget(edit)).isFalse();
	}

	@Test
	void url이_다르면_다른_대상이다() {
		DbConnection raw = connection("jdbc:oracle:thin:@localhost:1521/XE", "RAW");
		DbConnection edit = connection("jdbc:oracle:thin:@other:1521/XE", "RAW");

		assertThat(raw.sameTarget(edit)).isFalse();
	}

	@Test
	void toString에_비밀번호가_노출되지_않는다() {
		assertThat(connection("jdbc:oracle:thin:@localhost:1521/XE", "RAW").toString())
				.doesNotContain("pw");
	}
}
