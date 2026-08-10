package kr.co.promptech.privacy_eraser.migration.infrastructure;

import kr.co.promptech.privacy_eraser.migration.domain.ColumnMaskingStat;
import kr.co.promptech.privacy_eraser.migration.domain.CommentDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.ConstraintDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.ConstraintType;
import kr.co.promptech.privacy_eraser.migration.domain.IndexDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationTarget;
import kr.co.promptech.privacy_eraser.migration.domain.SequenceDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.SourceObjectReader;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * {@code ALL_*} 뷰를 읽어 제약조건·인덱스·코멘트·시퀀스 정의를 조립합니다.
 * <p>
 * {@code DBMS_METADATA} 를 쓰지 않는 이유는 {@code SELECT_CATALOG_ROLE} 이 필요해
 * 받은 DB 에서 기대하기 어렵기 때문입니다. 실제로 이관 계정으로는 ORA-31603 이 납니다.
 */
@Component
public class OracleSourceObjectReader implements SourceObjectReader {

	private static final int TIMEOUT_SECONDS = 30;

	/** 전수 집계는 테이블을 통째로 훑습니다. 메타데이터 조회와 같은 시간을 줄 수 없습니다. */
	private static final int SCAN_TIMEOUT_SECONDS = 600;

	/**
	 * NOT NULL 은 Oracle 이 CHECK 로 저장하지만 테이블을 만들 때 이미 따라오므로 제외합니다.
	 * 다시 만들면 중복입니다.
	 */
	private static final String CONSTRAINTS = """
			SELECT c.table_name,
			       c.constraint_name,
			       c.constraint_type,
			       c.search_condition_vc,
			       r.table_name AS referenced_table,
			       c.delete_rule,
			       LISTAGG(cc.column_name, ',') WITHIN GROUP (ORDER BY cc.position) AS columns,
			       (SELECT LISTAGG(rc.column_name, ',') WITHIN GROUP (ORDER BY rc.position)
			          FROM all_cons_columns rc
			         WHERE rc.owner = c.r_owner AND rc.constraint_name = c.r_constraint_name) AS referenced_columns
			  FROM all_constraints c
			  JOIN all_cons_columns cc
			    ON cc.owner = c.owner AND cc.constraint_name = c.constraint_name
			  LEFT JOIN all_constraints r
			    ON r.owner = c.r_owner AND r.constraint_name = c.r_constraint_name
			 WHERE c.owner = ?
			   AND c.constraint_type IN ('P', 'U', 'R', 'C')
			   AND c.table_name NOT LIKE 'BIN$%'
			   AND (c.constraint_type != 'C' OR c.search_condition_vc NOT LIKE '%IS NOT NULL')
			 GROUP BY c.table_name, c.constraint_name, c.constraint_type, c.search_condition_vc,
			          r.table_name, c.delete_rule, c.r_owner, c.r_constraint_name
			 ORDER BY c.table_name, c.constraint_name
			""";

	/**
	 * PK·UNIQUE 를 만들면 Oracle 이 인덱스를 자동으로 만듭니다.
	 * 제약조건과 이름이 같은 인덱스는 따로 만들면 안 됩니다.
	 */
	private static final String INDEXES = """
			SELECT i.table_name,
			       i.index_name,
			       i.uniqueness,
			       LISTAGG(ic.column_name, ',') WITHIN GROUP (ORDER BY ic.column_position) AS columns
			  FROM all_indexes i
			  JOIN all_ind_columns ic
			    ON ic.index_owner = i.owner AND ic.index_name = i.index_name
			 WHERE i.owner = ?
			   AND i.index_type = 'NORMAL'
			   AND i.table_name NOT LIKE 'BIN$%'
			   AND NOT EXISTS (SELECT 1 FROM all_constraints c
			                    WHERE c.owner = i.owner AND c.constraint_name = i.index_name)
			 GROUP BY i.table_name, i.index_name, i.uniqueness
			 ORDER BY i.table_name, i.index_name
			""";

	/**
	 * {@code PURGE} 없이 지운 테이블은 휴지통에 {@code BIN$...} 이름으로 남습니다.
	 * <p>
	 * ALL_TABLES·ALL_INDEXES·ALL_CONSTRAINTS 는 이것을 걸러 주는데
	 * <b>ALL_TAB_COMMENTS·ALL_COL_COMMENTS 는 걸러 주지 않습니다.</b>
	 * 그대로 두면 이관 대상에 없는 {@code BIN$...} 에 코멘트를 달려다 ORA-00942 가 납니다.
	 */
	private static final String COMMENTS = """
			SELECT table_name, NULL AS column_name, comments FROM all_tab_comments
			 WHERE owner = ? AND comments IS NOT NULL AND table_name NOT LIKE 'BIN$%'
			UNION ALL
			SELECT table_name, column_name, comments FROM all_col_comments
			 WHERE owner = ? AND comments IS NOT NULL AND table_name NOT LIKE 'BIN$%'
			""";

	private static final String SEQUENCES = """
			SELECT sequence_name, last_number, increment_by
			  FROM all_sequences WHERE sequence_owner = ?
			""";

	@Override
	public List<ConstraintDefinition> readConstraints(DbConnection raw) {
		return query(raw, CONSTRAINTS, 1, resultSet -> new ConstraintDefinition(
				resultSet.getString("table_name"),
				resultSet.getString("constraint_name"),
				toType(resultSet.getString("constraint_type")),
				split(resultSet.getString("columns")),
				resultSet.getString("search_condition_vc"),
				resultSet.getString("referenced_table"),
				split(resultSet.getString("referenced_columns")),
				resultSet.getString("delete_rule")));
	}

	@Override
	public List<IndexDefinition> readIndexes(DbConnection raw) {
		return query(raw, INDEXES, 1, resultSet -> new IndexDefinition(
				resultSet.getString("table_name"),
				resultSet.getString("index_name"),
				"UNIQUE".equals(resultSet.getString("uniqueness")),
				split(resultSet.getString("columns"))));
	}

	@Override
	public List<CommentDefinition> readComments(DbConnection raw) {
		return query(raw, COMMENTS, 2, resultSet -> new CommentDefinition(
				resultSet.getString("table_name"),
				resultSet.getString("column_name"),
				resultSet.getString("comments")));
	}

	@Override
	public List<SequenceDefinition> readSequences(DbConnection raw) {
		return query(raw, SEQUENCES, 1, resultSet -> new SequenceDefinition(
				resultSet.getString("sequence_name"),
				resultSet.getLong("last_number"),
				resultSet.getLong("increment_by")));
	}

	/**
	 * 마스킹 방향은 셀 때 상관없습니다. 앞이든 뒤든 값 길이가 정책 길이 이하면 통째로 가려집니다.
	 * <p>
	 * 컬럼마다 따로 세면 테이블을 그만큼 여러 번 훑습니다. 한 문장에 몰아 한 번만 읽습니다.
	 * NULL 은 마스킹해도 NULL 이라 세지 않습니다.
	 */
	@Override
	public List<ColumnMaskingStat> countMasking(DbConnection raw, MigrationTarget target) {
		List<MigrationTarget.Column> masked = target.columns().stream()
				.filter(column -> column.policy() != null)
				.toList();
		if (masked.isEmpty()) {
			return List.of();
		}

		StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS total_rows");
		for (int i = 0; i < masked.size(); i++) {
			sql.append(",\n       COUNT(CASE WHEN LENGTH(TO_CHAR(%s)) <= %d THEN 1 END) AS c%d".formatted(
					OracleMaskExpression.quote(masked.get(i).name()), masked.get(i).policy().length(), i));
		}
		sql.append("\n  FROM %s.%s".formatted(
				OracleMaskExpression.quote(raw.schema().toUpperCase(Locale.ROOT)),
				OracleMaskExpression.quote(target.tableName())));

		return query(raw, sql.toString(), 0, resultSet -> {
			long total = resultSet.getLong("total_rows");
			List<ColumnMaskingStat> stats = new ArrayList<>();
			for (int i = 0; i < masked.size(); i++) {
				stats.add(new ColumnMaskingStat(target.tableName(), masked.get(i).name(),
						total, resultSet.getLong("c" + i)));
			}
			return stats;
		}).stream().flatMap(List::stream).toList();
	}

	private static ConstraintType toType(String code) {
		return switch (code) {
			case "P" -> ConstraintType.PRIMARY_KEY;
			case "U" -> ConstraintType.UNIQUE;
			case "R" -> ConstraintType.FOREIGN_KEY;
			default -> ConstraintType.CHECK;
		};
	}

	private static List<String> split(String joined) {
		return joined == null || joined.isBlank() ? List.of() : Arrays.asList(joined.split(","));
	}

	@FunctionalInterface
	private interface RowMapper<T> {
		T map(ResultSet resultSet) throws SQLException;
	}

	/**
	 * @param schemaParameterCount 스키마명을 몇 번 바인딩하는지 (UNION 질의는 두 번)
	 */
	private static <T> List<T> query(DbConnection raw, String sql, int schemaParameterCount, RowMapper<T> mapper) {
		Properties properties = new Properties();
		properties.setProperty("user", raw.username());
		properties.setProperty("password", raw.password());

		try (Connection connection = DriverManager.getConnection(raw.url(), properties);
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setQueryTimeout(schemaParameterCount == 0 ? SCAN_TIMEOUT_SECONDS : TIMEOUT_SECONDS);
			for (int i = 1; i <= schemaParameterCount; i++) {
				statement.setString(i, raw.schema().toUpperCase(Locale.ROOT));
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				List<T> rows = new ArrayList<>();
				while (resultSet.next()) {
					rows.add(mapper.map(resultSet));
				}
				return rows;
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException(e.getMessage());
		}
	}
}
