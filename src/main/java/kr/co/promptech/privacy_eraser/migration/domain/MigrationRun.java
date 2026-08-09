package kr.co.promptech.privacy_eraser.migration.domain;

import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 이관 실행 한 건입니다. 수십 초에서 몇 분이 걸려 비동기로 돌리므로, 진행 상황을 여기에 남깁니다.
 */
@Getter
public class MigrationRun {

	private final Long id;
	private final Long projectId;
	private MigrationStatus status;
	private int totalTables;
	private int completedTables;
	private String currentTable;
	private String message;
	private final OffsetDateTime startedAt;
	private OffsetDateTime finishedAt;

	public MigrationRun(Long id, Long projectId, MigrationStatus status, int totalTables, int completedTables,
			String currentTable, String message, OffsetDateTime startedAt, OffsetDateTime finishedAt) {
		if (projectId == null) {
			throw new IllegalArgumentException("어느 프로젝트의 실행인지 필요합니다.");
		}
		this.id = id;
		this.projectId = projectId;
		this.status = status == null ? MigrationStatus.RUNNING : status;
		this.totalTables = totalTables;
		this.completedTables = completedTables;
		this.currentTable = currentTable;
		this.message = message;
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
	}

	public static MigrationRun start(Long projectId, OffsetDateTime now) {
		return new MigrationRun(null, projectId, MigrationStatus.RUNNING, 0, 0, null, null, now, null);
	}

	public void planned(int totalTables) {
		this.totalTables = totalTables;
	}

	public void working(String tableName) {
		this.currentTable = tableName;
	}

	public void tableDone() {
		this.completedTables++;
		this.currentTable = null;
	}

	public void succeeded(OffsetDateTime now) {
		this.status = MigrationStatus.SUCCEEDED;
		this.currentTable = null;
		this.finishedAt = now;
	}

	/**
	 * 실패 사유는 사용자가 고칠 수 있어야 하므로 그대로 남깁니다. 다만 길이는 자릅니다.
	 */
	public void failed(String reason, OffsetDateTime now) {
		this.status = MigrationStatus.FAILED;
		this.message = reason == null ? "알 수 없는 오류" : reason.substring(0, Math.min(reason.length(), 1900));
		this.finishedAt = now;
	}

	public boolean isRunning() {
		return status == MigrationStatus.RUNNING;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MigrationRun run) || id == null || run.id == null) {
			return false;
		}
		return id.equals(run.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "MigrationRun[id=%s, status=%s, %d/%d]".formatted(id, status, completedTables, totalTables);
	}
}
