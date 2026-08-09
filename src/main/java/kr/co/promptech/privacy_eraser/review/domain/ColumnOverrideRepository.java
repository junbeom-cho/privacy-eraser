package kr.co.promptech.privacy_eraser.review.domain;

import java.util.List;
import java.util.Optional;

public interface ColumnOverrideRepository {

	Long save(ColumnOverride override);

	void update(ColumnOverride override);

	void deleteById(Long id);

	/** 프로젝트의 사용자 지정을 모두 지웁니다. */
	void deleteAllByProjectId(Long projectId);

	List<ColumnOverride> findAllByProjectId(Long projectId);

	Optional<ColumnOverride> findOne(Long projectId, String tableName, String columnName);
}
