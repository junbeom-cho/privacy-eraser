package kr.co.promptech.privacy_eraser.project.infrastructure;

import kr.co.promptech.privacy_eraser.project.domain.ConnectionTestResult;
import kr.co.promptech.privacy_eraser.project.domain.ConnectionTester;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

@Component
public class JdbcConnectionTester implements ConnectionTester {

	private static final int TIMEOUT_SECONDS = 5;

	/**
	 * 접속과 스키마를 함께 확인합니다.
	 * <p>
	 * 계정만 맞으면 스키마명이 틀려도 접속 자체는 성공합니다. 그대로 두면 나중에 스키마를 읽을 때
	 * 빈 목록이 나와 원인을 알 수 없으므로, 여기서 스키마 존재와 테이블 수까지 확인합니다.
	 * <p>
	 * DB 종류를 가리지 않도록 {@link DatabaseMetaData} 를 씁니다. Oracle 전용 뷰(ALL_USERS)를
	 * 쓰면 나중에 다른 DB 를 추가할 때 여기부터 깨집니다.
	 */
	@Override
	public ConnectionTestResult test(DbConnection target) {
		Properties properties = new Properties();
		properties.setProperty("user", target.username());
		properties.setProperty("password", target.password());
		// ponytail: DriverManager 로그인 타임아웃은 JVM 전역입니다. 동시 테스트가 많아지면 DataSource별 설정으로 교체.
		DriverManager.setLoginTimeout(TIMEOUT_SECONDS);

		try (Connection connection = DriverManager.getConnection(target.url(), properties)) {
			if (!connection.isValid(TIMEOUT_SECONDS)) {
				return ConnectionTestResult.failed("접속은 되었으나 연결이 유효하지 않습니다.");
			}
			DatabaseMetaData metaData = connection.getMetaData();
			String actualSchema = findSchema(metaData, target.schema());
			if (actualSchema == null) {
				return ConnectionTestResult.schemaNotFound(target.schema());
			}
			return ConnectionTestResult.succeeded(target.schema(), countTables(metaData, actualSchema));
		}
		catch (SQLException e) {
			return ConnectionTestResult.failed(e.getMessage());
		}
	}

	/**
	 * DB 가 쓰는 실제 표기를 돌려줍니다. 우리는 스키마명을 대문자로 정규화하지만(Oracle 관례)
	 * PostgreSQL 의 {@code public} 처럼 소문자인 DB 도 있어서, 테이블 조회에는 DB 가 알려준 이름을 써야 합니다.
	 *
	 * @return 찾지 못하면 null
	 */
	private static String findSchema(DatabaseMetaData metaData, String schema) throws SQLException {
		try (ResultSet resultSet = metaData.getSchemas()) {
			while (resultSet.next()) {
				String candidate = resultSet.getString("TABLE_SCHEM");
				if (schema.equalsIgnoreCase(candidate)) {
					return candidate;
				}
			}
		}
		return null;
	}

	/**
	 * 0개여도 실패는 아닙니다. 이관 대상 스키마는 비어 있는 것이 정상이기 때문입니다.
	 * 원본을 잘못 지정한 경우인지는 사용자가 판단합니다.
	 */
	private static int countTables(DatabaseMetaData metaData, String schema) throws SQLException {
		int count = 0;
		try (ResultSet resultSet = metaData.getTables(null, schema, "%", new String[] { "TABLE" })) {
			while (resultSet.next()) {
				count++;
			}
		}
		return count;
	}
}
