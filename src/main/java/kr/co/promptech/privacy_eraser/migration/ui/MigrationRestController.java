package kr.co.promptech.privacy_eraser.migration.ui;

import kr.co.promptech.privacy_eraser.migration.application.MigrationService;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationRun;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/projects/{projectId}/migration")
public class MigrationRestController {

	private final MigrationService migrationService;

	/**
	 * 이관을 시작합니다. 수십 초에서 몇 분이 걸리므로 실행 ID 만 돌려주고,
	 * 화면은 상태를 따로 조회합니다.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public StartedResponse start(@PathVariable Long projectId) {
		return new StartedResponse(migrationService.start(projectId));
	}

	/**
	 * 가장 최근 실행의 상태입니다. 없으면 204 입니다.
	 */
	@GetMapping
	public MigrationRunResponse latest(@PathVariable Long projectId) {
		return migrationService.findLatest(projectId).map(MigrationRunResponse::from).orElse(null);
	}

	public record StartedResponse(Long runId) {
	}

	public record MigrationRunResponse(Long runId, MigrationStatus status, int totalTables, int completedTables,
			String currentTable, String message, OffsetDateTime startedAt, OffsetDateTime finishedAt) {

		static MigrationRunResponse from(MigrationRun run) {
			return new MigrationRunResponse(run.getId(), run.getStatus(), run.getTotalTables(),
					run.getCompletedTables(), run.getCurrentTable(), run.getMessage(),
					run.getStartedAt(), run.getFinishedAt());
		}
	}
}
