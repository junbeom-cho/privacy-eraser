package kr.co.promptech.privacy_eraser.schema.infrastructure;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnKey;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;
import kr.co.promptech.privacy_eraser.schema.domain.SchemaReader;
import kr.co.promptech.privacy_eraser.schema.domain.TableMetadata;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Component
public class JdbcSchemaReader implements SchemaReader {

	private static final int TIMEOUT_SECONDS = 10;

	/**
	 * 스키마 하나로 한정해서 조회합니다. 뷰는 제외하고 테이블만 봅니다.
	 * 스키마명은 바인딩 변수로 넘깁니다 - 문자열로 이어붙이면 SQL injection 입니다.
	 */
	private static final String SQL = """
			SELECT t.table_name,
			       c.column_name,
			       c.data_type,
			       c.char_length,
			       c.data_precision,
			       c.data_scale,
			       c.nullable
			  FROM all_tables t
			  JOIN all_tab_columns c
			    ON c.owner = t.owner
			   AND c.table_name = t.table_name
			 WHERE t.owner = ?
			   -- PURGE 없이 지운 테이블이 휴지통에 BIN$... 로 남습니다. 이관 대상이 아닙니다.
			   AND t.table_name NOT LIKE 'BIN$%'
			 ORDER BY t.table_name, c.column_id
			""";

	/**
	 * 컬럼에 걸린 키입니다. 한 컬럼이 PK 이면서 FK 일 수 있어 여러 줄이 나옵니다.
	 * CHECK 는 값이 겹치는 것과 무관해 보지 않습니다.
	 */
	private static final String KEYS = """
			SELECT cc.table_name,
			       cc.column_name,
			       con.constraint_type
			  FROM all_cons_columns cc
			  JOIN all_constraints con
			    ON con.owner = cc.owner
			   AND con.constraint_name = cc.constraint_name
			 WHERE cc.owner = ?
			   AND con.constraint_type IN ('P', 'U', 'R')
			""";

	@Override
	public List<TableMetadata> readTables(DbConnection target) {
		Properties properties = new Properties();
		properties.setProperty("user", target.username());
		properties.setProperty("password", target.password());
		DriverManager.setLoginTimeout(TIMEOUT_SECONDS);

		try (Connection connection = DriverManager.getConnection(target.url(), properties)) {
			// 같은 접속에서 두 번 읽고 합칩니다. 컬럼마다 상관 서브쿼리를 걸면 컬럼 수만큼 조회합니다.
			Map<String, Set<ColumnKey>> keys = readKeys(connection, target.schema());
			try (PreparedStatement statement = connection.prepareStatement(SQL)) {
				statement.setString(1, target.schema());
				statement.setQueryTimeout(TIMEOUT_SECONDS);
				try (ResultSet resultSet = statement.executeQuery()) {
					return toTables(resultSet, keys);
				}
			}
		}
		catch (SQLException e) {
			// 원인을 그대로 보여줘야 사용자가 접속 정보를 고칠 수 있습니다.
			throw new IllegalStateException(e.getMessage());
		}
	}

	private static Map<String, Set<ColumnKey>> readKeys(Connection connection, String schema) throws SQLException {
		Map<String, Set<ColumnKey>> keys = new HashMap<>();
		try (PreparedStatement statement = connection.prepareStatement(KEYS)) {
			statement.setString(1, schema);
			statement.setQueryTimeout(TIMEOUT_SECONDS);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					keys.computeIfAbsent(
							keyOf(resultSet.getString("table_name"), resultSet.getString("column_name")),
							name -> EnumSet.noneOf(ColumnKey.class))
							.add(toColumnKey(resultSet.getString("constraint_type")));
				}
			}
		}
		return keys;
	}

	static String keyOf(String tableName, String columnName) {
		return tableName + "." + columnName;
	}

	static ColumnKey toColumnKey(String constraintType) {
		return switch (constraintType) {
			case "P" -> ColumnKey.PRIMARY_KEY;
			case "U" -> ColumnKey.UNIQUE;
			default -> ColumnKey.FOREIGN_KEY;
		};
	}

	private static List<TableMetadata> toTables(ResultSet resultSet, Map<String, Set<ColumnKey>> keys)
			throws SQLException {
		// 테이블명 순으로 정렬해 읽으므로 입력 순서를 유지합니다.
		Map<String, List<ColumnMetadata>> byTable = new LinkedHashMap<>();
		while (resultSet.next()) {
			String tableName = resultSet.getString("table_name");
			ColumnMetadata column = toColumn(resultSet);
			byTable.computeIfAbsent(tableName, name -> new ArrayList<>())
					.add(column.withKeys(keys.getOrDefault(keyOf(tableName, column.name()), Set.of())));
		}
		return byTable.entrySet().stream()
				.map(entry -> new TableMetadata(entry.getKey(), entry.getValue()))
				.toList();
	}

	private static ColumnMetadata toColumn(ResultSet resultSet) throws SQLException {
		String name = resultSet.getString("column_name");
		String dataType = resultSet.getString("data_type");
		boolean nullable = "Y".equals(resultSet.getString("nullable"));

		// char_length 는 문자형에만 채워집니다. 숫자·날짜는 0 입니다.
		int charLength = resultSet.getInt("char_length");
		if (charLength > 0) {
			return ColumnMetadata.character(name, dataType, charLength, nullable);
		}
		Integer precision = nullableInt(resultSet, "data_precision");
		if (precision != null) {
			return ColumnMetadata.number(name, precision, nullableInt(resultSet, "data_scale"), nullable);
		}
		return ColumnMetadata.plain(name, dataType, nullable);
	}

	private static Integer nullableInt(ResultSet resultSet, String column) throws SQLException {
		int value = resultSet.getInt(column);
		return resultSet.wasNull() ? null : value;
	}
}
