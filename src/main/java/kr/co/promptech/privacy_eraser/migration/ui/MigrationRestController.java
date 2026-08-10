package kr.co.promptech.privacy_eraser.migration.ui;

import kr.co.promptech.privacy_eraser.migration.application.MigrationService;
import kr.co.promptech.privacy_eraser.migration.domain.ColumnMaskingStat;
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
import java.util.List;

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
		return migrationService.findLatest(projectId)
				.map(run -> MigrationRunResponse.from(run, migrationService.findStats(run.getId())))
				.orElse(null);
	}

	/**
	 * 이관 대상 스키마를 만드는 SQL 입니다. 도구는 실행하지 않습니다.
	 * 접속 비밀번호는 들어가지 않습니다 - 자리표시자로 나갑니다.
	 */
	@GetMapping("/setup-script")
	public SetupScriptResponse setupScript(@PathVariable Long projectId) {
		return new SetupScriptResponse(migrationService.editSchemaScript(projectId));
	}

	public record StartedResponse(Long runId) {
	}

	public record SetupScriptResponse(String script) {
	}

	/**
	 * @param stats 통째로 가려진 행이 있는 컬럼만 담깁니다. 없으면 빈 목록입니다.
	 */
	public record MigrationRunResponse(Long runId, MigrationStatus status, int totalTables, int completedTables,
			String currentTable, String message, OffsetDateTime startedAt, OffsetDateTime finishedAt,
			List<ColumnMaskingStat> stats) {

		static MigrationRunResponse from(MigrationRun run, List<ColumnMaskingStat> stats) {
			return new MigrationRunResponse(run.getId(), run.getStatus(), run.getTotalTables(),
					run.getCompletedTables(), run.getCurrentTable(), run.getMessage(),
					run.getStartedAt(), run.getFinishedAt(), stats);
		}
	}
}
