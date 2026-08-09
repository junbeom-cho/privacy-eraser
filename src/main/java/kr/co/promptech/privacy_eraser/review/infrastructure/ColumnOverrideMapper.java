package kr.co.promptech.privacy_eraser.review.infrastructure;

import kr.co.promptech.privacy_eraser.review.domain.ColumnOverride;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ColumnOverrideMapper {

	Long nextOverrideId();

	void insert(@Param("id") Long id, @Param("override") ColumnOverride override);

	void update(@Param("override") ColumnOverride override);

	void deleteById(@Param("id") Long id);

	void deleteAllByProjectId(@Param("projectId") Long projectId);

	List<ColumnOverrideRow> findAllByProjectId(@Param("projectId") Long projectId);

	ColumnOverrideRow findOne(@Param("projectId") Long projectId,
			@Param("tableName") String tableName, @Param("columnName") String columnName);
}
