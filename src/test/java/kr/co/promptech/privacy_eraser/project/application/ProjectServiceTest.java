package kr.co.promptech.privacy_eraser.project.application;

import kr.co.promptech.privacy_eraser.project.domain.ConnectionTestResult;
import kr.co.promptech.privacy_eraser.project.domain.ConnectionTester;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectServiceTest {

	private static final String URL = "jdbc:oracle:thin:@localhost:1521/XE";
	private static final DbConnection RAW = new DbConnection(URL, "app", "pw", "RAW_SCHEMA");

	private FakeProjectRepository repository;
	private FakeConnectionTester tester;
	private ProjectService service;

	@BeforeEach
	void setUp() {
		repository = new FakeProjectRepository();
		tester = new FakeConnectionTester();
		service = new ProjectService(repository, tester);
	}

	@Test
	void 원본_접속정보만으로_저장하고_id를_돌려준다() {
		Long id = service.create(new CreateProjectCommand("고객정보 비식별화", RAW));

		assertThat(id).isEqualTo(1L);
		assertThat(repository.saved).hasSize(1);
		assertThat(repository.saved.get(0).name()).isEqualTo("고객정보 비식별화");
		assertThat(repository.saved.get(0).hasEditConnection()).isFalse();
	}

	@Test
	void 이름이_중복되면_저장하지_않는다() {
		service.create(new CreateProjectCommand("중복", RAW));

		assertThatThrownBy(() -> service.create(new CreateProjectCommand("중복", RAW)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 존재하는");
		assertThat(repository.saved).hasSize(1);
	}

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

		@Override
		public Long save(Project project) {
			saved.add(project);
			return (long) saved.size();
		}

		@Override
		public boolean existsByName(String name) {
			return saved.stream().anyMatch(p -> p.name().equals(name));
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
