package kr.co.promptech.privacy_eraser.migration.application;

import kr.co.promptech.privacy_eraser.migration.domain.ConstraintDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.ConstraintType;
import kr.co.promptech.privacy_eraser.migration.domain.MaskingConflicts;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationExecutor;
import kr.co.promptech.privacy_eraser.migration.domain.SourceObjectReader;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationRun;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationRunRepository;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationTarget;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import kr.co.promptech.privacy_eraser.review.application.ReviewService;
import kr.co.promptech.privacy_eraser.review.domain.ColumnReview;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * 검수에서 확정된 결정대로 원본을 이관 대상으로 옮깁니다.
 * <p>
 * 마스킹 판정은 {@link ReviewService} 가 이미 하고 있으므로 여기서 다시 하지 않습니다.
 * 화면에서 본 것과 실제로 옮겨지는 것이 같아야 하기 때문입니다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MigrationService {

	private final ProjectRepository projectRepository;
	private final MigrationRunRepository runRepository;
	private final MigrationExecutor executor;
	private final ReviewService reviewService;
	private final SourceObjectReader sourceObjectReader;
	/**
	 * 실행을 별도 스레드로 넘깁니다. 필드명이 Spring 이 등록하는 빈 이름과 같아야 주입됩니다.
	 * 테스트에서는 {@code Runnable::run} 을 넣어 동기로 돌립니다.
	 */
	private final Executor applicationTaskExecutor;

	/**
	 * 이관을 시작합니다. 검증과 이력 생성은 지금 하고, 실제 작업은 별도 스레드로 넘깁니다.
	 * 수십 초에서 몇 분이 걸려 요청을 붙잡고 있으면 중간에 끊기기 때문입니다.
	 *
	 * @return 실행 ID. 화면은 이것으로 상태를 조회합니다.
	 */
	public Long start(Long projectId) {
		Project project = projectRepository.findById(projectId)
				.orElseThrow(() -> new ProjectNotFoundException(projectId));
		if (!project.hasEditConnection()) {
			throw new IllegalArgumentException("이관 대상 접속 정보를 먼저 등록하세요.");
		}
		if (runRepository.existsRunningByProjectId(projectId)) {
			throw new IllegalArgumentException("이미 실행 중입니다. 끝난 뒤에 다시 시도하세요.");
		}

		List<MigrationTarget> targets = plan(projectId);

		// 데이터를 다 옮긴 뒤에 제약조건이 실패하면 늦습니다. 미리 알 수 있는 충돌은 시작 전에 막습니다.
		List<ConstraintDefinition> constraints = sourceObjectReader.readConstraints(project.getRawConnection());
		List<String> conflicts = MaskingConflicts.find(constraints, targets);
		if (!conflicts.isEmpty()) {
			throw new IllegalArgumentException(String.join("\n", conflicts));
		}

		MigrationRun run = MigrationRun.start(projectId, OffsetDateTime.now());
		run.planned(targets.size());
		Long runId = runRepository.save(run);

		applicationTaskExecutor.execute(() -> execute(runId, project, targets, constraints));
		return runId;
	}

	public Optional<MigrationRun> findLatest(Long projectId) {
		return runRepository.findLatestByProjectId(projectId);
	}

	public Optional<MigrationRun> findById(Long runId) {
		return runRepository.findById(runId);
	}

	/**
	 * 검수 결과를 테이블 단위로 묶습니다. 원본의 모든 테이블이 대상이며,
	 * 마스킹 대상이 없는 테이블도 그대로 복사합니다.
	 */
	private void applyObjects(Project project, List<ConstraintDefinition> constraints) {
		var edit = project.getEditConnection();
		var raw = project.getRawConnection();

		// 적재가 끝난 뒤에 만듭니다. 적재 중에는 행마다 갱신 비용이 듭니다.
		sourceObjectReader.readIndexes(raw).forEach(index -> executor.createIndex(edit, index));

		// FK 는 참조 대상이 모두 적재된 뒤라야 걸립니다. 나머지를 먼저 겁니다.
		constraints.stream().filter(c -> c.type() != ConstraintType.FOREIGN_KEY)
				.forEach(constraint -> executor.addConstraint(edit, constraint));
		constraints.stream().filter(c -> c.type() == ConstraintType.FOREIGN_KEY)
				.forEach(constraint -> executor.addConstraint(edit, constraint));

		sourceObjectReader.readComments(raw).forEach(comment -> executor.applyComment(edit, comment));
		sourceObjectReader.readSequences(raw).forEach(sequence -> executor.createSequence(edit, sequence));
	}

	private List<MigrationTarget> plan(Long projectId) {
		Map<String, List<MigrationTarget.Column>> byTable = new LinkedHashMap<>();
		for (ColumnReview review : reviewService.review(projectId)) {
			byTable.computeIfAbsent(review.tableName(), name -> new java.util.ArrayList<>())
					.add(new MigrationTarget.Column(review.column().name(),
							review.decision().masked() ? review.decision().policy() : null));
		}
		return byTable.entrySet().stream()
				.map(entry -> new MigrationTarget(entry.getKey(), entry.getValue()))
				.toList();
	}

	/**
	 * 순서를 지켜야 합니다. 인덱스는 적재 후에 만들어야 빠르고, FK 는 모든 테이블이 적재된 뒤라야 겁니다.
	 * 하나라도 실패하면 전체를 실패로 봅니다. 일부만 걸린 스키마는 완전한 줄 알고 쓰이면 더 위험합니다.
	 */
	private void execute(Long runId, Project project, List<MigrationTarget> targets,
			List<ConstraintDefinition> constraints) {
		MigrationRun run = runRepository.findById(runId).orElseThrow();
		try {
			for (MigrationTarget target : targets) {
				run.working(target.tableName());
				runRepository.update(run);

				// 지우는 범위를 원본에 있는 테이블로 한정해, 접속을 잘못 넣었을 때 피해를 줄입니다.
				executor.dropIfExists(project.getEditConnection(), target.tableName());
				executor.createAndCopy(project.getRawConnection(), project.getEditConnection(), target);

				run.tableDone();
				runRepository.update(run);
			}

			run.working("인덱스·제약조건");
			runRepository.update(run);
			applyObjects(project, constraints);

			run.succeeded(OffsetDateTime.now());
		}
		catch (RuntimeException e) {
			log.error("이관에 실패했습니다. runId={}", runId, e);
			run.failed(e.getMessage(), OffsetDateTime.now());
		}
		runRepository.update(run);
	}
}
