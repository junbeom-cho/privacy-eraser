package kr.co.promptech.privacy_eraser.project.domain;

import java.util.List;
import java.util.Optional;

/**
 * 프로젝트 저장 포트입니다. 구현은 infrastructure(프로젝트 정보 DB)에 둡니다.
 */
public interface ProjectRepository {

	Long save(Project project);

	void update(Project project);

	void deleteById(Long id);

	List<Project> findAll();

	Optional<Project> findById(Long id);

	boolean existsByName(String name);
}
