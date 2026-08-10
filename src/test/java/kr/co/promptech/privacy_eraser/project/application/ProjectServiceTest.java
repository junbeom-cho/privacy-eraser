package kr.co.promptech.privacy_eraser.project.application;

import kr.co.promptech.privacy_eraser.project.domain.ConnectionTestResult;
import kr.co.promptech.privacy_eraser.project.domain.ConnectionTester;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectServiceTest {

	private static final String URL = "jdbc:oracle:thin:@localhost:1521/XE";
	private static final DbConnection RAW = new DbConnection(URL, "app", "pw", "RAW_SCHEMA");
	private static final DbConnection EDIT = new DbConnection(URL, "app", "editpw", "EDIT_SCHEMA");

	private FakeProjectRepository repository;
	private FakeConnectionTester tester;
	private ProjectService service;

	@BeforeEach
	void setUp() {
		repository = new FakeProjectRepository();
		tester = new FakeConnectionTester();
		service = new ProjectService(repository, tester);
	}

	private Long given원본만_가진_프로젝트(String name) {
		return service.create(new CreateProjectCommand(name, RAW, EDIT));
	}

	// ===== 생성 =====

	@Test
	void 원본_접속정보만으로_저장하고_id를_돌려준다() {
		Long id = given원본만_가진_프로젝트("고객정보 비식별화");

		assertThat(id).isEqualTo(1L);
		assertThat(repository.saved).hasSize(1);
		assertThat(repository.saved.get(0).getEditConnection()).isEqualTo(EDIT);
	}

	@Test
	void 이름이_중복되면_저장하지_않는다() {
		given원본만_가진_프로젝트("중복");

		assertThatThrownBy(() -> service.create(new CreateProjectCommand("중복", RAW, EDIT)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 존재하는");
		assertThat(repository.saved).hasSize(1);
	}

	// ===== 조회 =====

	@Test
	void 전체_목록을_돌려준다() {
		given원본만_가진_프로젝트("첫번째");
		given원본만_가진_프로젝트("두번째");

		assertThat(service.findAll()).extracting(Project::getName).containsExactly("첫번째", "두번째");
	}

	@Test
	void 단건을_돌려준다() {
		Long id = given원본만_가진_프로젝트("고객정보 비식별화");

		assertThat(service.findById(id).getName()).isEqualTo("고객정보 비식별화");
	}

	@Test
	void 없는_id를_조회하면_예외다() {
		assertThatThrownBy(() -> service.findById(999L))
				.isInstanceOf(ProjectNotFoundException.class);
	}

	// ===== 삭제 =====

	@Test
	void 삭제한다() {
		Long id = given원본만_가진_프로젝트("지울것");

		service.delete(id);

		assertThat(service.findAll()).isEmpty();
	}

	@Test
	void 없는_id를_삭제하면_예외다() {
		assertThatThrownBy(() -> service.delete(999L))
				.isInstanceOf(ProjectNotFoundException.class);
	}

	// ===== 수정 =====

	@Test
	void 이름과_원본_접속정보를_수정한다() {
		Long id = given원본만_가진_프로젝트("옛이름");
		DbConnection 새원본 = new DbConnection(URL, "newuser", "newpw", "NEW_SCHEMA");

		service.update(new UpdateProjectCommand(id, "새이름", 새원본, EDIT));

		Project updated = service.findById(id);
		assertThat(updated.getName()).isEqualTo("새이름");
		assertThat(updated.getRawConnection().username()).isEqualTo("newuser");
		assertThat(updated.getRawConnection().password()).isEqualTo("newpw");
	}

	@Test
	void 비밀번호를_비우면_기존_값을_유지한다() {
		Long id = given원본만_가진_프로젝트("이름");
		DbConnection 비밀번호_없이 = new DbConnection(URL, "app", "", "RAW_SCHEMA");

		service.update(new UpdateProjectCommand(id, "이름", 비밀번호_없이, EDIT));

		assertThat(service.findById(id).getRawConnection().password()).isEqualTo("pw");
	}

	@Test
	void 이관_대상을_나중에_채울_수_있다() {
		Long id = given원본만_가진_프로젝트("이름");

		service.update(new UpdateProjectCommand(id, "이름", RAW, EDIT));

		Project updated = service.findById(id);
		assertThat(updated.getEditConnection()).isNotNull();
		assertThat(updated.getEditConnection().schema()).isEqualTo("EDIT_SCHEMA");
	}

	@Test
	void 이관_대상이_원본과_같으면_수정할_수_없다() {
		Long id = given원본만_가진_프로젝트("이름");

		assertThatThrownBy(() -> service.update(new UpdateProjectCommand(id, "이름", RAW, RAW)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("원본");
	}

	@Test
	void 다른_프로젝트와_이름이_겹치면_수정할_수_없다() {
		given원본만_가진_프로젝트("먼저");
		Long id = given원본만_가진_프로젝트("나중");

		assertThatThrownBy(() -> service.update(new UpdateProjectCommand(id, "먼저", RAW, EDIT)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 존재하는");
	}

	@Test
	void 자기_이름_그대로_수정하는_것은_허용한다() {
		Long id = given원본만_가진_프로젝트("그대로");

		service.update(new UpdateProjectCommand(id, "그대로", RAW, EDIT));

		assertThat(service.findById(id).getName()).isEqualTo("그대로");
	}

	@Test
	void 없는_id를_수정하면_예외다() {
		assertThatThrownBy(() -> service.update(new UpdateProjectCommand(999L, "이름", RAW, EDIT)))
				.isInstanceOf(ProjectNotFoundException.class);
	}

	// ===== 접속 테스트 =====

	@Test
	void 접속_테스트에_성공하면_성공_결과를_돌려준다() {
		tester.result = ConnectionTestResult.succeeded();

		assertThat(service.testConnection(RAW).success()).isTrue();
		assertThat(tester.tested).isEqualTo(RAW);
	}

	@Test
	void 접속_테스트에_실패하면_실패_사유를_돌려준다() {
		tester.result = ConnectionTestResult.failed("ORA-01017: invalid credential");

		ConnectionTestResult result = service.testConnection(RAW);

		assertThat(result.success()).isFalse();
		assertThat(result.message()).contains("ORA-01017");
	}

	private static class FakeProjectRepository implements ProjectRepository {
		private final List<Project> saved = new ArrayList<>();
		private final AtomicLong sequence = new AtomicLong();

		@Override
		public Long save(Project project) {
			Long id = sequence.incrementAndGet();
			saved.add(new Project(id, project.getName(), project.getRawConnection(), project.getEditConnection()));
			return id;
		}

		@Override
		public void update(Project project) {
			deleteById(project.getId());
			saved.add(project);
		}

		@Override
		public void deleteById(Long id) {
			saved.removeIf(p -> p.getId().equals(id));
		}

		@Override
		public List<Project> findAll() {
			return List.copyOf(saved);
		}

		@Override
		public Optional<Project> findById(Long id) {
			return saved.stream().filter(p -> p.getId().equals(id)).findFirst();
		}

		@Override
		public boolean existsByName(String name) {
			return saved.stream().anyMatch(p -> p.getName().equals(name));
		}
	}

	private static class FakeConnectionTester implements ConnectionTester {
		private ConnectionTestResult result = ConnectionTestResult.succeeded();
		private DbConnection tested;

		@Override
		public ConnectionTestResult test(DbConnection connection) {
			this.tested = connection;
			return result;
		}
	}
}
