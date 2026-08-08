package kr.co.promptech.privacy_eraser.project.domain;

public record ConnectionTestResult(boolean success, String message) {

	public static ConnectionTestResult succeeded() {
		return new ConnectionTestResult(true, "접속에 성공했습니다.");
	}

	public static ConnectionTestResult failed(String message) {
		return new ConnectionTestResult(false, message);
	}
}
