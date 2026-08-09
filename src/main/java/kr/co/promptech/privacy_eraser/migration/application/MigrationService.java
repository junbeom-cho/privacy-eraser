package kr.co.promptech.privacy_eraser.migration.application;

import kr.co.promptech.privacy_eraser.migration.domain.MigrationExecutor;
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
		MigrationRun run = MigrationRun.start(projectId, OffsetDateTime.now());
		run.planned(targets.size());
		Long runId = runRepository.save(run);

		applicationTaskExecutor.execute(() -> execute(runId, project, targets));
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

	private void execute(Long runId, Project project, List<MigrationTarget> targets) {
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
			run.succeeded(OffsetDateTime.now());
		}
		catch (RuntimeException e) {
			log.error("이관에 실패했습니다. runId={}", runId, e);
			run.failed(e.getMessage(), OffsetDateTime.now());
		}
		runRepository.update(run);
	}
}
