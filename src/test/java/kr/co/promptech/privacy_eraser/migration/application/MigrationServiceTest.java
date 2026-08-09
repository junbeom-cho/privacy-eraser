package kr.co.promptech.privacy_eraser.migration.application;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.migration.domain.CommentDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.ConstraintDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.ConstraintType;
import kr.co.promptech.privacy_eraser.migration.domain.IndexDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationExecutor;
import kr.co.promptech.privacy_eraser.migration.domain.SequenceDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.SourceObjectReader;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationRun;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationRunRepository;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationStatus;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationTarget;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import kr.co.promptech.privacy_eraser.review.application.ReviewService;
import kr.co.promptech.privacy_eraser.review.domain.ColumnReview;
import kr.co.promptech.privacy_eraser.review.domain.MaskingDecision;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

class MigrationServiceTest {

	private static final String URL = "jdbc:oracle:thin:@localhost:1521/XE";
	private static final DbConnection RAW = new DbConnection(URL, "hr", "pw", "HR");
	private static final DbConnection EDIT = new DbConnection(URL, "hr", "pw", "HR_EDIT");
	private static final MaskingPolicy 뒤_4자리 = new MaskingPolicy(MaskingDirection.FROM_END, 4);

	private FakeProjectRepository projects;
	private FakeRunRepository runs;
	private RecordingExecutor executor;
	private ReviewService reviewService;
	private FakeSourceObjectReader source;
	private MigrationService service;

	@BeforeEach
	void setUp() {
		projects = new FakeProjectRepository();
		runs = new FakeRunRepository();
		executor = new RecordingExecutor();
		source = new FakeSourceObjectReader();
		reviewService = Mockito.mock(ReviewService.class);
		projects.saved.add(new Project(1L, "프로젝트", RAW, EDIT));
		given(reviewService.review(1L)).willReturn(List.of(
				review("EMPLOYEES", "EMPLOYEE_ID", null),
				review("EMPLOYEES", "PHONE_NUMBER", 뒤_4자리),
				review("DEPARTMENTS", "DEPARTMENT_ID", null)));
		// 테스트에서는 같은 스레드에서 돌려 결과를 바로 확인합니다.
		service = new MigrationService(projects, runs, executor, reviewService, source, Runnable::run);
	}

	private static ColumnReview review(String table, String column, MaskingPolicy policy) {
		ColumnMetadata metadata = ColumnMetadata.character(column, "VARCHAR2", 50, true);
		MaskingDecision decision = policy == null
				? MaskingDecision.notMasked()
				: MaskingDecision.byDoKeyword("phone", policy);
		return new ColumnReview(table, metadata, decision, null);
	}

	@Test
	void 원본의_모든_테이블을_옮긴다() {
		service.start(1L);

		// 마스킹 대상이 없는 테이블도 그대로 복사합니다.
		assertThat(executor.created).extracting(MigrationTarget::tableName)
				.containsExactly("EMPLOYEES", "DEPARTMENTS");
	}

	@Test
	void 마스킹_대상만_정책을_달고_나머지는_그대로_옮긴다() {
		service.start(1L);

		MigrationTarget employees = executor.created.get(0);
		assertThat(employees.columns()).extracting(MigrationTarget.Column::name)
				.containsExactly("EMPLOYEE_ID", "PHONE_NUMBER");
		assertThat(employees.columns().get(0).policy()).isNull();
		assertThat(employees.columns().get(1).policy()).isEqualTo(뒤_4자리);
	}

	@Test
	void 만들기_전에_같은_이름의_테이블을_지운다() {
		service.start(1L);

		assertThat(executor.dropped).containsExactly("EMPLOYEES", "DEPARTMENTS");
	}

	@Test
	void 성공하면_상태와_진행_수를_남긴다() {
		Long runId = service.start(1L);

		MigrationRun run = runs.findById(runId).orElseThrow();
		assertThat(run.getStatus()).isEqualTo(MigrationStatus.SUCCEEDED);
		assertThat(run.getTotalTables()).isEqualTo(2);
		assertThat(run.getCompletedTables()).isEqualTo(2);
		assertThat(run.getFinishedAt()).isNotNull();
	}

	@Test
	void 실패하면_사유를_남기고_멈춘다() {
		executor.failOn = "DEPARTMENTS";

		Long runId = service.start(1L);

		MigrationRun run = runs.findById(runId).orElseThrow();
		assertThat(run.getStatus()).isEqualTo(MigrationStatus.FAILED);
		assertThat(run.getMessage()).contains("ORA-01031");
		assertThat(run.getCompletedTables()).isEqualTo(1);
	}

	@Test
	void 이관_대상이_없으면_실행할_수_없다() {
		projects.saved.set(0, new Project(1L, "프로젝트", RAW, null));

		assertThatThrownBy(() -> service.start(1L))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이관 대상");
	}

	@Test
	void 이미_실행_중이면_또_실행할_수_없다() {
		runs.saved.add(new MigrationRun(99L, 1L, MigrationStatus.RUNNING, 0, 0, null, null, null, null));

		assertThatThrownBy(() -> service.start(1L))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("실행 중");
	}

	@Test
	void 인덱스와_제약조건을_적재_뒤에_만들고_FK_는_맨_마지막이다() {
		source.constraints = List.of(
				new ConstraintDefinition("EMPLOYEES", "EMP_FK", ConstraintType.FOREIGN_KEY,
						List.of("DEPARTMENT_ID"), null, "DEPARTMENTS", List.of("DEPARTMENT_ID"), "NO ACTION"),
				new ConstraintDefinition("EMPLOYEES", "EMP_PK", ConstraintType.PRIMARY_KEY,
						List.of("EMPLOYEE_ID"), null, null, null, null));

		service.start(1L);

		assertThat(executor.applied).containsExactly(
				"INDEX EMP_NAME_IX", "PRIMARY_KEY EMP_PK", "FOREIGN_KEY EMP_FK",
				"COMMENT EMPLOYEES", "SEQUENCE EMP_SEQ");
	}

	@Test
	void 마스킹된_컬럼에_UNIQUE_가_걸려_있으면_시작하지_않는다() {
		source.constraints = List.of(new ConstraintDefinition("EMPLOYEES", "EMP_EMAIL_UK", ConstraintType.UNIQUE,
				List.of("PHONE_NUMBER"), null, null, null, null));

		assertThatThrownBy(() -> service.start(1L))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("EMP_EMAIL_UK");
		// 시작 전에 막았으므로 아무것도 옮기지 않습니다.
		assertThat(executor.created).isEmpty();
	}

	@Test
	void 제약조건_하나가_실패하면_전체가_실패다() {
		source.constraints = List.of(new ConstraintDefinition("EMPLOYEES", "EMP_PK", ConstraintType.PRIMARY_KEY,
				List.of("EMPLOYEE_ID"), null, null, null, null));
		executor.failOn = "EMP_PK";

		Long runId = service.start(1L);

		MigrationRun run = runs.findById(runId).orElseThrow();
		assertThat(run.getStatus()).isEqualTo(MigrationStatus.FAILED);
		assertThat(run.getMessage()).contains("ORA-02299");
	}

	@Test
	void 없는_프로젝트는_실행할_수_없다() {
		assertThatThrownBy(() -> service.start(999L)).isInstanceOf(ProjectNotFoundException.class);
	}

	@Test
	void 최근_실행을_조회한다() {
		Long runId = service.start(1L);

		assertThat(service.findLatest(1L)).isPresent()
				.get().extracting(MigrationRun::getId).isEqualTo(runId);
	}

	private static class RecordingExecutor implements MigrationExecutor {
		private final List<String> dropped = new ArrayList<>();
		private final List<MigrationTarget> created = new ArrayList<>();
		private final List<String> applied = new ArrayList<>();
		private String failOn;

		@Override
		public void dropIfExists(DbConnection edit, String tableName) {
			dropped.add(tableName);
		}

		@Override
		public void createAndCopy(DbConnection raw, DbConnection edit, MigrationTarget target) {
			if (target.tableName().equals(failOn)) {
				throw new IllegalStateException("ORA-01031: insufficient privileges");
			}
			created.add(target);
		}

		@Override
		public void createIndex(DbConnection edit, IndexDefinition index) {
			applied.add("INDEX " + index.name());
		}

		@Override
		public void addConstraint(DbConnection edit, ConstraintDefinition constraint) {
			if (constraint.name().equals(failOn)) {
				throw new IllegalStateException("ORA-02299: duplicate keys found");
			}
			applied.add(constraint.type() + " " + constraint.name());
		}

		@Override
		public void applyComment(DbConnection edit, CommentDefinition comment) {
			applied.add("COMMENT " + comment.tableName());
		}

		@Override
		public void createSequence(DbConnection edit, SequenceDefinition sequence) {
			applied.add("SEQUENCE " + sequence.name());
		}
	}

	private static class FakeSourceObjectReader implements SourceObjectReader {
		private List<ConstraintDefinition> constraints = List.of();

		@Override
		public List<ConstraintDefinition> readConstraints(DbConnection raw) {
			return constraints;
		}

		@Override
		public List<IndexDefinition> readIndexes(DbConnection raw) {
			return List.of(new IndexDefinition("EMPLOYEES", "EMP_NAME_IX", false, List.of("EMPLOYEE_ID")));
		}

		@Override
		public List<CommentDefinition> readComments(DbConnection raw) {
			return List.of(new CommentDefinition("EMPLOYEES", null, "직원"));
		}

		@Override
		public List<SequenceDefinition> readSequences(DbConnection raw) {
			return List.of(new SequenceDefinition("EMP_SEQ", 1, 1));
		}
	}

	private static class FakeRunRepository implements MigrationRunRepository {
		private final List<MigrationRun> saved = new ArrayList<>();
		private final AtomicLong sequence = new AtomicLong();

		@Override
		public Long save(MigrationRun run) {
			Long id = sequence.incrementAndGet();
			saved.add(new MigrationRun(id, run.getProjectId(), run.getStatus(), run.getTotalTables(),
					run.getCompletedTables(), run.getCurrentTable(), run.getMessage(),
					run.getStartedAt(), run.getFinishedAt()));
			return id;
		}

		@Override
		public void update(MigrationRun run) {
			saved.removeIf(r -> r.getId().equals(run.getId()));
			saved.add(run);
		}

		@Override
		public Optional<MigrationRun> findById(Long id) {
			return saved.stream().filter(r -> r.getId().equals(id)).findFirst();
		}

		@Override
		public Optional<MigrationRun> findLatestByProjectId(Long projectId) {
			return saved.stream().filter(r -> r.getProjectId().equals(projectId))
					.reduce((first, second) -> second);
		}

		@Override
		public boolean existsRunningByProjectId(Long projectId) {
			return saved.stream().anyMatch(r -> r.getProjectId().equals(projectId) && r.isRunning());
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
}
