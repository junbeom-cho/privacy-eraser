package kr.co.promptech.privacy_protector.project.domain;

/**
 * 비식별화 프로젝트. raw_schema에서 읽어 edit_schema로 이관하는 한 벌의 설정을 소유한다.
 */
public record Project(Long id, String name, DbConnection rawConnection, DbConnection editConnection) {

	public Project {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("프로젝트 이름은 비워둘 수 없습니다.");
		}
		if (rawConnection == null || editConnection == null) {
			throw new IllegalArgumentException("raw/edit 접속 정보가 모두 필요합니다.");
		}
		if (rawConnection.sameTarget(editConnection)) {
			throw new IllegalArgumentException("원본(raw)과 이관 대상(edit)이 같은 스키마입니다. 원본을 덮어쓰게 되므로 사용할 수 없습니다.");
		}
		name = name.strip();
	}

	public static Project create(String name, DbConnection rawConnection, DbConnection editConnection) {
		return new Project(null, name, rawConnection, editConnection);
	}
}
