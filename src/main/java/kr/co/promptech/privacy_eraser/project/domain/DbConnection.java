package kr.co.promptech.privacy_eraser.project.domain;

import java.util.Locale;

/**
 * DB 접속 정보 값 객체. raw_schema / edit_schema 양쪽 모두 이 타입으로 다룬다.
 */
public record DbConnection(String url, String username, String password, String schema) {

	public DbConnection {
		if (url == null || !url.strip().startsWith("jdbc:")) {
			throw new IllegalArgumentException("JDBC URL은 jdbc: 로 시작해야 합니다.");
		}
		if (isBlank(username)) {
			throw new IllegalArgumentException("사용자명은 비워둘 수 없습니다.");
		}
		if (password == null) {
			throw new IllegalArgumentException("비밀번호가 필요합니다.");
		}
		if (isBlank(schema)) {
			throw new IllegalArgumentException("스키마는 비워둘 수 없습니다.");
		}
		url = url.strip();
		username = username.strip();
		schema = schema.strip().toUpperCase(Locale.ROOT); // 오라클 식별자는 대문자 기준
	}

	/**
	 * 같은 DB의 같은 스키마를 가리키는지. 프로젝트의 raw/edit이 이 관계면 원본을 덮어쓰게 된다.
	 */
	public boolean sameTarget(DbConnection other) {
		return other != null && url.equals(other.url) && schema.equals(other.schema);
	}

	@Override
	public String toString() {
		// 기본 record toString은 비밀번호를 그대로 찍는다. 로그 유출 방지를 위해 반드시 가린다.
		return "DbConnection[url=%s, username=%s, schema=%s]".formatted(url, username, schema);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
