package kr.co.promptech.privacy_protector.project.domain;

/**
 * 비식별화 프로젝트. raw_schema에서 읽어 edit_schema로 이관하는 한 벌의 설정입니다.
 * <p>
 * 이관 대상({@code editConnection})은 선택값입니다. 프로젝트를 만드는 시점에는 아직 어떤 테이블을
 * 대상으로 할지 정해지지 않았으므로, 원본을 탐색한 뒤 이관을 실행할 때 정하면 됩니다.
 * 다만 값이 있다면 원본과 같은 스키마일 수 없습니다.
 */
public record Project(Long id, String name, DbConnection rawConnection, DbConnection editConnection) {

	public Project {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("프로젝트 이름은 비워둘 수 없습니다.");
		}
		if (rawConnection == null) {
			throw new IllegalArgumentException("원본(raw) 접속 정보가 필요합니다.");
		}
		if (rawConnection.sameTarget(editConnection)) {
			throw new IllegalArgumentException("원본(raw)과 이관 대상(edit)이 같은 스키마입니다. 원본을 덮어쓰게 되므로 사용할 수 없습니다.");
		}
		name = name.strip();
	}

	public static Project create(String name, DbConnection rawConnection) {
		return new Project(null, name, rawConnection, null);
	}

	public boolean hasEditConnection() {
		return editConnection != null;
	}
}
