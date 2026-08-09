package kr.co.promptech.privacy_eraser.migration.infrastructure;

import kr.co.promptech.privacy_eraser.migration.domain.ColumnMaskingStat;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationRun;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class MigrationRunRepositoryImpl implements MigrationRunRepository {

	private final MigrationRunMapper migrationRunMapper;

	@Override
	public Long save(MigrationRun run) {
		Long id = migrationRunMapper.nextRunId();
		migrationRunMapper.insert(id, run);
		return id;
	}

	@Override
	public void update(MigrationRun run) {
		migrationRunMapper.update(run);
	}

	@Override
	public Optional<MigrationRun> findById(Long id) {
		return Optional.ofNullable(migrationRunMapper.findById(id)).map(MigrationRunRow::toDomain);
	}

	@Override
	public Optional<MigrationRun> findLatestByProjectId(Long projectId) {
		return Optional.ofNullable(migrationRunMapper.findLatestByProjectId(projectId))
				.map(MigrationRunRow::toDomain);
	}

	@Override
	public boolean existsRunningByProjectId(Long projectId) {
		return migrationRunMapper.existsRunningByProjectId(projectId);
	}

	@Override
	public void saveStats(Long runId, List<ColumnMaskingStat> stats) {
		if (!stats.isEmpty()) {
			migrationRunMapper.insertStats(runId, stats);
		}
	}

	@Override
	public List<ColumnMaskingStat> findStats(Long runId) {
		return migrationRunMapper.findStats(runId);
	}
}
