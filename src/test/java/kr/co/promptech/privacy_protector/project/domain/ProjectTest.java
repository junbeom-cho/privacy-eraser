package kr.co.promptech.privacy_protector.project.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectTest {

	private static final String URL = "jdbc:oracle:thin:@localhost:1521/XE";
	private static final DbConnection RAW = new DbConnection(URL, "app", "pw", "RAW_SCHEMA");
	private static final DbConnection EDIT = new DbConnection(URL, "app", "pw", "EDIT_SCHEMA");

	@Test
	void 프로젝트를_생성한다() {
		Project project = Project.create("이륜차 공공데이터", RAW, EDIT);

		assertThat(project.id()).isNull();
		assertThat(project.name()).isEqualTo("이륜차 공공데이터");
		assertThat(project.rawConnection()).isEqualTo(RAW);
		assertThat(project.editConnection()).isEqualTo(EDIT);
	}

	@Test
	void 이름이_비어있으면_생성할_수_없다() {
		assertThatThrownBy(() -> Project.create(" ", RAW, EDIT))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void raw와_edit이_같은_대상이면_생성할_수_없다() {
		assertThatThrownBy(() -> Project.create("덮어쓰기 사고", RAW, RAW))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("원본");
	}

	@Test
	void 접속정보가_없으면_생성할_수_없다() {
		assertThatThrownBy(() -> Project.create("이름", null, EDIT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Project.create("이름", RAW, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 이름_앞뒤_공백은_제거한다() {
		assertThat(Project.create("  프로젝트  ", RAW, EDIT).name()).isEqualTo("프로젝트");
	}
}
