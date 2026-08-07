package kr.co.promptech.privacy_protector.project.infrastructure;

import kr.co.promptech.privacy_protector.project.domain.ConnectionTestResult;
import kr.co.promptech.privacy_protector.project.domain.ConnectionTester;
import kr.co.promptech.privacy_protector.project.domain.DbConnection;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@Component
public class JdbcConnectionTester implements ConnectionTester {

	private static final int TIMEOUT_SECONDS = 5;

	@Override
	public ConnectionTestResult test(DbConnection target) {
		Properties properties = new Properties();
		properties.setProperty("user", target.username());
		properties.setProperty("password", target.password());
		// ponytail: DriverManager 로그인 타임아웃은 JVM 전역이다. 동시 테스트가 많아지면 DataSource별 설정으로 교체.
		DriverManager.setLoginTimeout(TIMEOUT_SECONDS);

		try (Connection connection = DriverManager.getConnection(target.url(), properties)) {
			return connection.isValid(TIMEOUT_SECONDS)
					? ConnectionTestResult.succeeded()
					: ConnectionTestResult.failed("접속은 되었으나 연결이 유효하지 않습니다.");
		}
		catch (SQLException e) {
			return ConnectionTestResult.failed(e.getMessage());
		}
	}
}
