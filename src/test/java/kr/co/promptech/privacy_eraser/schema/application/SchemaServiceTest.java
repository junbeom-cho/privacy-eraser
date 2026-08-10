package kr.co.promptech.privacy_eraser.schema.application;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;
import kr.co.promptech.privacy_eraser.schema.domain.SchemaReader;
import kr.co.promptech.privacy_eraser.schema.domain.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaServiceTest {

	private static final String URL = "jdbc:oracle:thin:@localhost:1521/XE";
	private static final DbConnection RAW = new DbConnection(URL, "hr", "pw", "HR");
	private static final DbConnection EDIT = new DbConnection(URL, "hr", "pw", "EDIT_SCHEMA");

	private FakeProjectRepository projects;
	private FakeSchemaReader reader;
	private SchemaService service;

	@BeforeEach
	void setUp() {
		projects = new FakeProjectRepository();
		reader = new FakeSchemaReader();
		service = new SchemaService(projects, reader);
	}

	@Test
	void 프로젝트의_원본_스키마를_읽는다() {
		projects.saved.add(new Project(1L, "이름", RAW, EDIT));
		reader.tables = List.of(new TableMetadata("EMPLOYEES",
				List.of(ColumnMetadata.number("EMPLOYEE_ID", 6, 0, false))));

		List<TableMetadata> tables = service.readTables(1L);

		assertThat(tables).extracting(TableMetadata::name).containsExactly("EMPLOYEES");
	}

	@Test
	void 원본_접속정보로_읽는다() {
		projects.saved.add(new Project(1L, "이름", RAW, EDIT));

		service.readTables(1L);

		assertThat(reader.used).isEqualTo(RAW);
	}

	@Test
	void 없는_프로젝트면_예외다() {
		assertThatThrownBy(() -> service.readTables(999L))
				.isInstanceOf(ProjectNotFoundException.class);
	}

	@Test
	void 원본_DB_조회에_실패하면_사유를_그대로_전한다() {
		// 접속 정보가 틀린 것이므로 삼키지 않고 올려보내야 사용자가 고칠 수 있습니다.
		projects.saved.add(new Project(1L, "이름", RAW, EDIT));
		reader.failure = new IllegalStateException("ORA-01017: invalid credential");

		assertThatThrownBy(() -> service.readTables(1L))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ORA-01017");
	}

	@Test
	void 테이블이_하나도_없으면_빈_목록이다() {
		projects.saved.add(new Project(1L, "이름", RAW, EDIT));
		reader.tables = List.of();

		assertThat(service.readTables(1L)).isEmpty();
	}

	private static class FakeProjectRepository implements ProjectRepository {
		private final List<Project> saved = new ArrayList<>();

		@Override
		public Optional<Project> findById(Long id) {
			return saved.stream().filter(p -> p.getId().equals(id)).findFirst();
		}

		@Override
		public Long save(Project project) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void update(Project project) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void deleteById(Long id) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<Project> findAll() {
			return List.copyOf(saved);
		}

		@Override
		public boolean existsByName(String name) {
			return false;
		}
	}

	private static class FakeSchemaReader implements SchemaReader {
		private List<TableMetadata> tables = List.of();
		private RuntimeException failure;
		private DbConnection used;

		@Override
		public List<TableMetadata> readTables(DbConnection connection) {
			this.used = connection;
			if (failure != null) {
				throw failure;
			}
			return tables;
		}
	}
}
