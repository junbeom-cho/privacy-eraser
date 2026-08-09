package kr.co.promptech.privacy_eraser.migration.infrastructure;

import kr.co.promptech.privacy_eraser.migration.domain.MigrationRun;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationStatus;

import java.time.OffsetDateTime;

public record MigrationRunRow(Long runId, Long projectId, String status, int totalTables, int completedTables,
		String currentTable, String message, OffsetDateTime startedAt, OffsetDateTime finishedAt) {

	MigrationRun toDomain() {
		return new MigrationRun(runId, projectId, MigrationStatus.valueOf(status), totalTables, completedTables,
				currentTable, message, startedAt, finishedAt);
	}
}
