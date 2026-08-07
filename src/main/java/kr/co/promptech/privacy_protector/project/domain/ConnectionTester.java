package kr.co.promptech.privacy_protector.project.domain;

/**
 * 접속 확인 포트. 구현은 infrastructure에 둔다.
 */
public interface ConnectionTester {

	ConnectionTestResult test(DbConnection connection);
}
