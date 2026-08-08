package kr.co.promptech.privacy_eraser.project.domain;

import java.util.Objects;

/**
 * 비식별화 프로젝트. raw_schema에서 읽어 edit_schema로 이관하는 한 벌의 설정입니다.
 * <p>
 * 엔티티라 record가 아니라 클래스입니다. id로 식별되고, 생명주기 동안 이름과 접속 정보가 바뀝니다.
 * 동등성도 값 전체가 아니라 id로만 판단합니다.
 * <p>
 * 이관 대상({@code editConnection})은 선택값입니다. 프로젝트를 만드는 시점에는 아직 어떤 테이블을
 * 대상으로 할지 정해지지 않았으므로, 원본을 탐색한 뒤 이관을 실행할 때 정하면 됩니다.
 * 다만 값이 있다면 원본과 같은 스키마일 수 없습니다.
 */
public class Project {

	private final Long id;
	private String name;
	private DbConnection rawConnection;
	private DbConnection editConnection;

	public Project(Long id, String name, DbConnection rawConnection, DbConnection editConnection) {
		this.id = id;
		assign(name, rawConnection, editConnection);
	}

	public static Project create(String name, DbConnection rawConnection) {
		return new Project(null, name, rawConnection, null);
	}

	/**
	 * 이름과 접속 정보를 한 번에 바꿉니다. 원본·이관 대상 검사가 짝으로 이루어져야 해서
	 * 따로 바꾸는 메서드를 두지 않습니다.
	 */
	public void update(String name, DbConnection rawConnection, DbConnection editConnection) {
		assign(name, rawConnection, editConnection);
	}

	private void assign(String name, DbConnection rawConnection, DbConnection editConnection) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("프로젝트 이름은 비워둘 수 없습니다.");
		}
		if (rawConnection == null) {
			throw new IllegalArgumentException("원본(raw) 접속 정보가 필요합니다.");
		}
		if (rawConnection.sameTarget(editConnection)) {
			throw new IllegalArgumentException("원본(raw)과 이관 대상(edit)이 같은 스키마입니다. 원본을 덮어쓰게 되므로 사용할 수 없습니다.");
		}
		this.name = name.strip();
		this.rawConnection = rawConnection;
		this.editConnection = editConnection;
	}

	public boolean hasEditConnection() {
		return editConnection != null;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public DbConnection getRawConnection() {
		return rawConnection;
	}

	public DbConnection getEditConnection() {
		return editConnection;
	}

	/**
	 * 엔티티이므로 id로만 비교합니다. 아직 저장되지 않아 id가 없으면 같은 객체일 때만 같습니다.
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof Project project) || id == null || project.id == null) {
			return false;
		}
		return id.equals(project.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "Project[id=%s, name=%s]".formatted(id, name);
	}
}
