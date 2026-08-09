package kr.co.promptech.privacy_eraser.migration.domain;

import java.util.List;
import java.util.Optional;

public interface MigrationRunRepository {

	Long save(MigrationRun run);

	void update(MigrationRun run);

	Optional<MigrationRun> findById(Long id);

	/** 프로젝트의 가장 최근 실행입니다. 화면이 이것 하나만 보여줍니다. */
	Optional<MigrationRun> findLatestByProjectId(Long projectId);

	boolean existsRunningByProjectId(Long projectId);

	void saveStats(Long runId, List<ColumnMaskingStat> stats);

	List<ColumnMaskingStat> findStats(Long runId);
}
