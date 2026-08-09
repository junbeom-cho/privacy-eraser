package kr.co.promptech.privacy_eraser.review.application;

import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordRepository;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import kr.co.promptech.privacy_eraser.review.domain.ColumnOverride;
import kr.co.promptech.privacy_eraser.review.domain.ColumnOverrideRepository;
import kr.co.promptech.privacy_eraser.review.domain.ColumnReview;
import kr.co.promptech.privacy_eraser.review.domain.DecisionSource;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;
import kr.co.promptech.privacy_eraser.schema.domain.SampleReader;
import kr.co.promptech.privacy_eraser.schema.domain.SchemaReader;
import kr.co.promptech.privacy_eraser.schema.domain.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewServiceTest {

	private static final MaskingPolicy 뒤_4자리 = new MaskingPolicy(MaskingDirection.FROM_END, 4);
	private static final MaskingPolicy 앞_1자리 = new MaskingPolicy(MaskingDirection.FROM_START, 1);
	private static final DbConnection RAW =
			new DbConnection("jdbc:oracle:thin:@localhost:1521/XE", "hr", "pw", "HR");

	private FakeProjectRepository projects;
	private FakeKeywordRepository keywords;
	private FakeOverrideRepository overrides;
	private FakeSchemaReader reader;
	private FakeSampleReader samples;
	private ReviewService service;

	@BeforeEach
	void setUp() {
		projects = new FakeProjectRepository();
		keywords = new FakeKeywordRepository();
		overrides = new FakeOverrideRepository();
		reader = new FakeSchemaReader();
		samples = new FakeSampleReader();
		projects.saved.add(new Project(1L, "프로젝트", RAW, null));
		reader.tables = List.of(new TableMetadata("EMPLOYEES", List.of(
				ColumnMetadata.number("EMPLOYEE_ID", 6, 0, false),
				ColumnMetadata.character("PHONE_NUMBER", "VARCHAR2", 20, true),
				ColumnMetadata.number("SALARY", 8, 2, true))));
		service = new ReviewService(projects, keywords, overrides, reader, samples);
	}

	private ColumnReview reviewOf(String columnName) {
		return service.review(1L).stream()
				.filter(row -> row.column().name().equals(columnName))
				.findFirst()
				.orElseThrow();
	}

	@Test
	void 원본의_모든_컬럼을_돌려준다() {
		assertThat(service.review(1L)).hasSize(3);
	}

	@Test
	void 키워드로_판정한_결과를_담는다() {
		keywords.saved.add(Keyword.markFor(1L, "phone", 뒤_4자리));
		keywords.saved.add(Keyword.skipFor(1L, "id"));

		assertThat(reviewOf("PHONE_NUMBER").decision().masked()).isTrue();
		assertThat(reviewOf("EMPLOYEE_ID").decision().source()).isEqualTo(DecisionSource.UNDO_KEYWORD);
		assertThat(reviewOf("SALARY").decision().source()).isEqualTo(DecisionSource.NO_MATCH);
	}

	@Test
	void 사용자_지정이_키워드_판정을_덮어쓴다() {
		keywords.saved.add(Keyword.skipFor(1L, "id"));
		overrides.saved.add(new ColumnOverride(1L, 1L, "EMPLOYEES", "EMPLOYEE_ID", true, 앞_1자리));

		assertThat(reviewOf("EMPLOYEE_ID").decision().source()).isEqualTo(DecisionSource.USER);
		assertThat(reviewOf("EMPLOYEE_ID").decision().masked()).isTrue();
	}

	@Test
	void 정책이_컬럼_길이보다_길면_알린다() {
		keywords.saved.add(Keyword.markFor(1L, "phone", new MaskingPolicy(MaskingDirection.FROM_END, 50)));

		assertThat(reviewOf("PHONE_NUMBER").policyExceedsColumnLength()).isTrue();
		assertThat(reviewOf("SALARY").policyExceedsColumnLength()).isFalse();
	}

	@Test
	void 없는_프로젝트는_예외다() {
		assertThatThrownBy(() -> service.review(999L)).isInstanceOf(ProjectNotFoundException.class);
	}

	// ===== 표본 데이터 =====

	@Test
	void 표본_값과_마스킹된_결과를_함께_돌려준다() {
		keywords.saved.add(Keyword.markFor(1L, "phone", 뒤_4자리));
		samples.row = Map.of("PHONE_NUMBER", "01012345678");

		assertThat(reviewOf("PHONE_NUMBER").sample()).isEqualTo("01012345678");
		assertThat(reviewOf("PHONE_NUMBER").maskedSample()).isEqualTo("0101234****");
	}

	@Test
	void 마스킹_대상이_아니면_표본이_그대로다() {
		samples.row = Map.of("SALARY", "24000");

		assertThat(reviewOf("SALARY").maskedSample()).isEqualTo("24000");
	}

	@Test
	void 표본이_통째로_가려지면_알린다() {
		keywords.saved.add(Keyword.markFor(1L, "phone", 뒤_4자리));
		samples.row = Map.of("PHONE_NUMBER", "123");

		assertThat(reviewOf("PHONE_NUMBER").maskedSample()).isEqualTo("***");
		assertThat(reviewOf("PHONE_NUMBER").sampleFullyMasked()).isTrue();
	}

	@Test
	void 표본을_읽지_못해도_검수는_된다() {
		// 권한이 없거나 테이블이 비어 있을 수 있습니다. 그래도 판정은 보여야 합니다.
		samples.failure = new IllegalStateException("ORA-00942: table or view does not exist");

		assertThat(service.review(1L)).hasSize(3);
		assertThat(reviewOf("SALARY").sample()).isNull();
	}

	// ===== 사용자 지정 저장 =====

	@Test
	void 사용자_지정을_새로_저장한다() {
		service.override(1L, new SaveOverrideCommand("employees", "phone_number", true, 뒤_4자리));

		assertThat(overrides.saved).singleElement().satisfies(o -> {
			// Oracle 관례에 맞춰 대문자로 저장합니다.
			assertThat(o.getTableName()).isEqualTo("EMPLOYEES");
			assertThat(o.getColumnName()).isEqualTo("PHONE_NUMBER");
		});
	}

	@Test
	void 이미_있으면_새로_만들지_않고_바꾼다() {
		service.override(1L, new SaveOverrideCommand("EMPLOYEES", "PHONE_NUMBER", true, 뒤_4자리));
		service.override(1L, new SaveOverrideCommand("EMPLOYEES", "PHONE_NUMBER", false, null));

		assertThat(overrides.saved).hasSize(1);
		assertThat(overrides.saved.get(0).isMasked()).isFalse();
		assertThat(overrides.saved.get(0).getPolicy()).isNull();
	}

	@Test
	void 마스킹하면서_정책이_없으면_거부한다() {
		assertThatThrownBy(() -> service.override(1L, new SaveOverrideCommand("EMPLOYEES", "PHONE_NUMBER", true, null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 원본에_없는_컬럼은_지정할_수_없다() {
		assertThatThrownBy(() -> service.override(1L, new SaveOverrideCommand("EMPLOYEES", "없는컬럼", true, 뒤_4자리)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("찾을 수 없습니다");
	}

	@Test
	void 지정을_지우면_키워드_판정으로_돌아간다() {
		keywords.saved.add(Keyword.skipFor(1L, "id"));
		service.override(1L, new SaveOverrideCommand("EMPLOYEES", "EMPLOYEE_ID", true, 앞_1자리));
		assertThat(reviewOf("EMPLOYEE_ID").decision().source()).isEqualTo(DecisionSource.USER);

		service.clearOverride(1L, "EMPLOYEES", "EMPLOYEE_ID");

		assertThat(reviewOf("EMPLOYEE_ID").decision().source()).isEqualTo(DecisionSource.UNDO_KEYWORD);
	}

	@Test
	void 없는_지정을_지워도_조용히_넘어간다() {
		service.clearOverride(1L, "EMPLOYEES", "SALARY");

		assertThat(overrides.saved).isEmpty();
	}

	private static class FakeOverrideRepository implements ColumnOverrideRepository {
		private final List<ColumnOverride> saved = new ArrayList<>();
		private final AtomicLong sequence = new AtomicLong();

		@Override
		public Long save(ColumnOverride override) {
			Long id = sequence.incrementAndGet();
			saved.add(new ColumnOverride(id, override.getProjectId(), override.getTableName(),
					override.getColumnName(), override.isMasked(), override.getPolicy()));
			return id;
		}

		@Override
		public void update(ColumnOverride override) {
			saved.removeIf(o -> o.getId().equals(override.getId()));
			saved.add(override);
		}

		@Override
		public void deleteById(Long id) {
			saved.removeIf(o -> o.getId().equals(id));
		}

		@Override
		public List<ColumnOverride> findAllByProjectId(Long projectId) {
			return saved.stream().filter(o -> o.getProjectId().equals(projectId)).toList();
		}

		@Override
		public Optional<ColumnOverride> findOne(Long projectId, String tableName, String columnName) {
			return saved.stream()
					.filter(o -> o.getProjectId().equals(projectId)
							&& o.getTableName().equalsIgnoreCase(tableName)
							&& o.getColumnName().equalsIgnoreCase(columnName))
					.findFirst();
		}
	}

	private static class FakeKeywordRepository implements KeywordRepository {
		private final List<Keyword> saved = new ArrayList<>();

		@Override
		public List<Keyword> findAllByProjectId(Long projectId) {
			return List.copyOf(saved);
		}

		@Override
		public Long save(Keyword keyword) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void update(Keyword keyword) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void deleteById(Long id) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<Keyword> findById(Long id) {
			return Optional.empty();
		}

		@Override
		public boolean existsByProjectIdAndWord(Long projectId, String word) {
			return false;
		}
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

	private static class FakeSampleReader implements SampleReader {
		private Map<String, String> row = Map.of();
		private RuntimeException failure;

		@Override
		public Map<String, String> readSampleRow(DbConnection connection, String tableName) {
			if (failure != null) {
				throw failure;
			}
			return row;
		}
	}

	private static class FakeSchemaReader implements SchemaReader {
		private List<TableMetadata> tables = List.of();

		@Override
		public List<TableMetadata> readTables(DbConnection connection) {
			return tables;
		}
	}
}
