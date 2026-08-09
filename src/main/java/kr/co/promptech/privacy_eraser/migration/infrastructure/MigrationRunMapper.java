package kr.co.promptech.privacy_eraser.migration.infrastructure;

import kr.co.promptech.privacy_eraser.migration.domain.MigrationRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MigrationRunMapper {

	Long nextRunId();

	void insert(@Param("id") Long id, @Param("run") MigrationRun run);

	void update(@Param("run") MigrationRun run);

	MigrationRunRow findById(@Param("id") Long id);

	MigrationRunRow findLatestByProjectId(@Param("projectId") Long projectId);

	boolean existsRunningByProjectId(@Param("projectId") Long projectId);
}
