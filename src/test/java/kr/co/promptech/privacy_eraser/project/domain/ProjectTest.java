package kr.co.promptech.privacy_eraser.project.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectTest {

	private static final String URL = "jdbc:oracle:thin:@localhost:1521/XE";
	private static final DbConnection RAW = new DbConnection(URL, "app", "pw", "RAW_SCHEMA");
	private static final DbConnection EDIT = new DbConnection(URL, "app", "pw", "EDIT_SCHEMA");

	@Test
	void 원본_접속정보만으로_생성한다() {
		Project project = Project.create("고객정보 비식별화", RAW);

		assertThat(project.id()).isNull();
		assertThat(project.name()).isEqualTo("고객정보 비식별화");
		assertThat(project.rawConnection()).isEqualTo(RAW);
		assertThat(project.editConnection()).isNull();
	}

	@Test
	void 이관_대상은_나중에_정해도_된다() {
		assertThat(Project.create("이름", RAW).hasEditConnection()).isFalse();
	}

	@Test
	void 이관_대상을_함께_넣어_생성할_수도_있다() {
		Project project = new Project(null, "이름", RAW, EDIT);

		assertThat(project.hasEditConnection()).isTrue();
		assertThat(project.editConnection()).isEqualTo(EDIT);
	}

	@Test
	void 이관_대상이_원본과_같으면_생성할_수_없다() {
		assertThatThrownBy(() -> new Project(null, "덮어쓰기 사고", RAW, RAW))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("원본");
	}

	@Test
	void 이관_대상이_없으면_같음_검사를_하지_않는다() {
		assertThatCode(() -> Project.create("이름", RAW)).doesNotThrowAnyException();
	}

	@Test
	void 이름이_비어있으면_생성할_수_없다() {
		assertThatThrownBy(() -> Project.create(" ", RAW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 원본_접속정보가_없으면_생성할_수_없다() {
		assertThatThrownBy(() -> Project.create("이름", null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 이름_앞뒤_공백은_제거한다() {
		assertThat(Project.create("  프로젝트  ", RAW).name()).isEqualTo("프로젝트");
	}
}
