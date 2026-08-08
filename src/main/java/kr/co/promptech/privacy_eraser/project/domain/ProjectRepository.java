package kr.co.promptech.privacy_eraser.project.domain;

/**
 * 프로젝트 저장 포트. 구현은 infrastructure(프로젝트 정보 DB)에 둔다.
 */
public interface ProjectRepository {

	Long save(Project project);

	boolean existsByName(String name);
}
