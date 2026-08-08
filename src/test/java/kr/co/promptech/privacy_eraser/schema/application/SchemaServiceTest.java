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
		projects.saved.add(new Project(1L, "이름", RAW, null));
		reader.tables = List.of(new TableMetadata("EMPLOYEES",
				List.of(ColumnMetadata.number("EMPLOYEE_ID", 6, 0, false))));

		List<TableMetadata> tables = service.readTables(1L);

		assertThat(tables).extracting(TableMetadata::name).containsExactly("EMPLOYEES");
	}

	@Test
	void 원본_접속정보로_읽는다() {
		projects.saved.add(new Project(1L, "이름", RAW, null));

		service.readTables(1L);

		assertThat(reader.used).isEqualTo(RAW);
	}

	@Test
	void 없는_프로젝트면_예외다() {
		assertThatThrownBy(() -> service.readTables(999L))
				.isInstanceOf(ProjectNotFoundException.class);
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
		private DbConnection used;

		@Override
		public List<TableMetadata> readTables(DbConnection connection) {
			this.used = connection;
			return tables;
		}
	}
}
