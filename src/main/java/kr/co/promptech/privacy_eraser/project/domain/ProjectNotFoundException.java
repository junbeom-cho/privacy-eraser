package kr.co.promptech.privacy_eraser.project.domain;

public class ProjectNotFoundException extends RuntimeException {

	public ProjectNotFoundException(Long id) {
		super("프로젝트를 찾을 수 없습니다. (id: %d)".formatted(id));
	}
}
