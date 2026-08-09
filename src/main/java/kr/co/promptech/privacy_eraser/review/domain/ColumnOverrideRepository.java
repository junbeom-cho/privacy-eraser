package kr.co.promptech.privacy_eraser.review.domain;

import java.util.List;
import java.util.Optional;

public interface ColumnOverrideRepository {

	Long save(ColumnOverride override);

	void update(ColumnOverride override);

	void deleteById(Long id);

	List<ColumnOverride> findAllByProjectId(Long projectId);

	Optional<ColumnOverride> findOne(Long projectId, String tableName, String columnName);
}
