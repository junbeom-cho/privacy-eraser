package kr.co.promptech.privacy_eraser.project.infrastructure;

import kr.co.promptech.privacy_eraser.project.domain.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectMapper {

	Long nextProjectId();

	/**
	 * 비밀번호는 도메인에 평문으로 들어있으므로 암호문을 따로 받습니다.
	 */
	void insert(@Param("id") Long id, @Param("project") Project project,
			@Param("rawPassword") String rawPassword, @Param("editPassword") String editPassword);

	void update(@Param("project") Project project,
			@Param("rawPassword") String rawPassword, @Param("editPassword") String editPassword);

	void deleteById(@Param("id") Long id);

	List<ProjectRow> findAll();

	ProjectRow findById(@Param("id") Long id);

	boolean existsByName(@Param("name") String name);
}
