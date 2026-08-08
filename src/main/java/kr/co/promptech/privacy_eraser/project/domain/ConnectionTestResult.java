package kr.co.promptech.privacy_eraser.project.domain;

public record ConnectionTestResult(boolean success, String message) {

	/**
	 * 접속만으로는 부족합니다. 스키마명이 틀려도 계정만 맞으면 접속은 되기 때문에,
	 * 어느 스키마에서 테이블이 몇 개 보이는지까지 알려야 사용자가 제대로 짚었는지 압니다.
	 */
	public static ConnectionTestResult succeeded(String schema, int tableCount) {
		return new ConnectionTestResult(true,
				"접속에 성공했습니다. (%s 스키마, 테이블 %d개)".formatted(schema, tableCount));
	}

	public static ConnectionTestResult succeeded() {
		return new ConnectionTestResult(true, "접속에 성공했습니다.");
	}

	/**
	 * 계정은 맞지만 그 스키마가 없거나 볼 권한이 없는 경우입니다.
	 */
	public static ConnectionTestResult schemaNotFound(String schema) {
		return new ConnectionTestResult(false,
				"접속은 되었지만 '%s' 스키마를 찾을 수 없습니다. 스키마명을 확인하거나 조회 권한이 있는지 확인하세요.".formatted(schema));
	}

	public static ConnectionTestResult failed(String message) {
		return new ConnectionTestResult(false, message);
	}
}
