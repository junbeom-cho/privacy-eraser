package kr.co.promptech.privacy_eraser.schema.infrastructure;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.schema.domain.SampleReader;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Component
public class JdbcSampleReader implements SampleReader {

	private static final int TIMEOUT_SECONDS = 10;
	private static final int MAX_VALUE_LENGTH = 100;

	@Override
	public Map<String, String> readSampleRow(DbConnection target, String tableName) {
		Properties properties = new Properties();
		properties.setProperty("user", target.username());
		properties.setProperty("password", target.password());
		DriverManager.setLoginTimeout(TIMEOUT_SECONDS);

		try (Connection connection = DriverManager.getConnection(target.url(), properties)) {
			// 테이블명은 바인딩 변수로 넘길 수 없습니다. 메타데이터에 실제로 있는 이름만 쓰도록 먼저 확인합니다.
			String verified = verifiedTableName(connection, target.schema(), tableName);
			return readFirstRow(connection, target.schema(), verified);
		}
		catch (SQLException e) {
			throw new IllegalStateException(e.getMessage());
		}
	}

	/**
	 * 사용자가 넣은 문자열을 SQL 에 그대로 붙이면 injection 입니다.
	 * DB 메타데이터에 존재하는 테이블만 통과시켜, 이어붙이는 값이 우리가 확인한 식별자가 되게 합니다.
	 */
	private static String verifiedTableName(Connection connection, String schema, String tableName)
			throws SQLException {
		try (ResultSet tables = connection.getMetaData().getTables(null, schema, "%", new String[] { "TABLE" })) {
			while (tables.next()) {
				String candidate = tables.getString("TABLE_NAME");
				if (candidate.equalsIgnoreCase(tableName.strip())) {
					return candidate;
				}
			}
		}
		throw new IllegalStateException("원본에서 %s 테이블을 찾을 수 없습니다.".formatted(tableName));
	}

	private static Map<String, String> readFirstRow(Connection connection, String schema, String tableName)
			throws SQLException {
		String sql = "SELECT * FROM \"%s\".\"%s\" WHERE ROWNUM = 1"
				.formatted(schema.toUpperCase(Locale.ROOT), tableName);

		Map<String, String> row = new LinkedHashMap<>();
		try (Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(TIMEOUT_SECONDS);
			statement.setMaxRows(1);
			try (ResultSet resultSet = statement.executeQuery(sql)) {
				ResultSetMetaData metaData = resultSet.getMetaData();
				if (!resultSet.next()) {
					return Map.of();
				}
				for (int i = 1; i <= metaData.getColumnCount(); i++) {
					row.put(metaData.getColumnName(i), truncate(resultSet.getString(i)));
				}
			}
		}
		return row;
	}

	/** 화면에 보여줄 용도라 긴 값(CLOB 등)은 잘라 보냅니다. */
	private static String truncate(String value) {
		if (value == null || value.length() <= MAX_VALUE_LENGTH) {
			return value;
		}
		return value.substring(0, MAX_VALUE_LENGTH) + "…";
	}
}
