package kr.co.promptech.privacy_protector.project.infrastructure;

import kr.co.promptech.privacy_protector.project.domain.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectMapper {

	Long nextProjectId();

	/**
	 * 비밀번호는 도메인에 평문으로 들어있으므로 암호문을 따로 받는다.
	 */
	void insert(@Param("id") Long id, @Param("project") Project project,
			@Param("rawPassword") String rawPassword, @Param("editPassword") String editPassword);

	boolean existsByName(@Param("name") String name);
}
