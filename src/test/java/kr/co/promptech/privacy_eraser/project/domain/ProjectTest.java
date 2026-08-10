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
		Project project = Project.create("고객정보 비식별화", RAW, EDIT);

		assertThat(project.getId()).isNull();
		assertThat(project.getName()).isEqualTo("고객정보 비식별화");
		assertThat(project.getRawConnection()).isEqualTo(RAW);
		assertThat(project.getEditConnection()).isNull();
	}

	@Test
	void 이관_대상을_함께_받는다() {
		assertThat(Project.create("이름", RAW, EDIT).getEditConnection()).isEqualTo(EDIT);
	}

	@Test
	void 이관_대상을_함께_넣어_생성할_수도_있다() {
		Project project = new Project(null, "이름", RAW, EDIT);

		assertThat(project.getEditConnection()).isEqualTo(EDIT);
		assertThat(project.getEditConnection()).isEqualTo(EDIT);
	}

	@Test
	void 이관_대상이_원본과_같으면_생성할_수_없다() {
		assertThatThrownBy(() -> new Project(null, "덮어쓰기 사고", RAW, RAW))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("원본");
	}

	@Test
	void 이관_대상이_없으면_같음_검사를_하지_않는다() {
		assertThatCode(() -> Project.create("이름", RAW, EDIT)).doesNotThrowAnyException();
	}

	@Test
	void 이름이_비어있으면_생성할_수_없다() {
		assertThatThrownBy(() -> Project.create(" ", RAW, EDIT))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 원본_접속정보가_없으면_생성할_수_없다() {
		assertThatThrownBy(() -> Project.create("이름", null, EDIT))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 이름_앞뒤_공백은_제거한다() {
		assertThat(Project.create("  프로젝트  ", RAW, EDIT).getName()).isEqualTo("프로젝트");
	}

	@Test
	void 이관_대상_없이는_만들_수_없다() {
		// 선택값으로 두면 NULL 분기가 도메인부터 화면까지 번집니다. 어차피 있어야 하는 값입니다.
		assertThatThrownBy(() -> new Project(1L, "프로젝트", RAW, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이관 대상");
	}

	@Test
	void 이관_대상을_지우도록_수정할_수_없다() {
		Project project = new Project(1L, "프로젝트", RAW, EDIT);

		assertThatThrownBy(() -> project.update("프로젝트", RAW, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이관 대상");
	}
}
