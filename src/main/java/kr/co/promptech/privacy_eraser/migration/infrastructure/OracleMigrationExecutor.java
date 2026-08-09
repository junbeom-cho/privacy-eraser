package kr.co.promptech.privacy_eraser.migration.infrastructure;

import kr.co.promptech.privacy_eraser.migration.domain.CommentDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.ConstraintDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.ConstraintType;
import kr.co.promptech.privacy_eraser.migration.domain.IndexDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationExecutor;
import kr.co.promptech.privacy_eraser.migration.domain.SequenceDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationTarget;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * 원본과 이관 대상이 같은 Oracle 인스턴스라는 전제로, 데이터를 애플리케이션으로 꺼내지 않고
 * {@code CREATE TABLE ... AS SELECT} 한 문장으로 구조와 데이터를 함께 만듭니다.
 * <p>
 * 접속은 <b>이관 대상 계정</b>으로 합니다. 원본에는 SELECT 권한만 있으면 되고,
 * 쓰기는 이관 대상 스키마에서만 일어납니다.
 */
@Component
public class OracleMigrationExecutor implements MigrationExecutor {

	private static final int TIMEOUT_SECONDS = 600;

	@Override
	public void dropIfExists(DbConnection edit, String tableName) {
		// 없는 테이블을 지우면 ORA-00942 가 납니다. 첫 실행에서는 정상이므로 무시합니다.
		String sql = """
				BEGIN
				  EXECUTE IMMEDIATE 'DROP TABLE %s.%s CASCADE CONSTRAINTS PURGE';
				EXCEPTION WHEN OTHERS THEN
				  IF SQLCODE != -942 THEN RAISE; END IF;
				END;""".formatted(schema(edit), OracleMaskExpression.quote(tableName));
		execute(edit, sql);
	}

	@Override
	public void createAndCopy(DbConnection raw, DbConnection edit, MigrationTarget target) {
		String selectList = target.columns().stream()
				.map(column -> "%s AS %s".formatted(
						OracleMaskExpression.of(column.name(), column.policy()),
						OracleMaskExpression.quote(column.name())))
				.collect(Collectors.joining(",\n       "));

		String sql = """
				CREATE TABLE %s.%s AS
				SELECT %s
				  FROM %s.%s""".formatted(
				schema(edit), OracleMaskExpression.quote(target.tableName()),
				selectList,
				schema(raw), OracleMaskExpression.quote(target.tableName()));
		execute(edit, sql);
	}

	@Override
	public void createIndex(DbConnection edit, IndexDefinition index) {
		execute(edit, "CREATE %sINDEX %s.%s ON %s.%s (%s)".formatted(
				index.unique() ? "UNIQUE " : "",
				schema(edit), OracleMaskExpression.quote(index.name()),
				schema(edit), OracleMaskExpression.quote(index.tableName()),
				columnList(index.columns())));
	}

	@Override
	public void addConstraint(DbConnection edit, ConstraintDefinition constraint) {
		execute(edit, "ALTER TABLE %s.%s ADD CONSTRAINT %s %s".formatted(
				schema(edit), OracleMaskExpression.quote(constraint.tableName()),
				OracleMaskExpression.quote(constraint.name()),
				clauseOf(edit, constraint)));
	}

	private static String clauseOf(DbConnection edit, ConstraintDefinition constraint) {
		return switch (constraint.type()) {
			case PRIMARY_KEY -> "PRIMARY KEY (%s)".formatted(columnList(constraint.columns()));
			case UNIQUE -> "UNIQUE (%s)".formatted(columnList(constraint.columns()));
			case CHECK -> "CHECK (%s)".formatted(constraint.checkExpression());
			// 참조 대상도 이관 대상 스키마 안의 테이블입니다. 원본을 가리키면 안 됩니다.
			case FOREIGN_KEY -> "FOREIGN KEY (%s) REFERENCES %s.%s (%s)%s".formatted(
					columnList(constraint.columns()),
					schema(edit), OracleMaskExpression.quote(constraint.referencedTable()),
					columnList(constraint.referencedColumns()),
					"CASCADE".equalsIgnoreCase(constraint.deleteRule()) ? " ON DELETE CASCADE" : "");
		};
	}

	@Override
	public void applyComment(DbConnection edit, CommentDefinition comment) {
		String target = comment.isTableComment()
				? "TABLE %s.%s".formatted(schema(edit), OracleMaskExpression.quote(comment.tableName()))
				: "COLUMN %s.%s.%s".formatted(schema(edit), OracleMaskExpression.quote(comment.tableName()),
						OracleMaskExpression.quote(comment.columnName()));
		// 코멘트 본문은 사용자 데이터가 아니라 스키마 설명입니다. 따옴표만 이스케이프합니다.
		execute(edit, "COMMENT ON %s IS '%s'".formatted(target, comment.comment().replace("'", "''")));
	}

	@Override
	public void createSequence(DbConnection edit, SequenceDefinition sequence) {
		execute(edit, "CREATE SEQUENCE %s.%s START WITH %d INCREMENT BY %d".formatted(
				schema(edit), OracleMaskExpression.quote(sequence.name()),
				Math.max(sequence.startWith(), 1), sequence.incrementBy()));
	}

	private static String columnList(java.util.List<String> columns) {
		return columns.stream().map(OracleMaskExpression::quote).collect(Collectors.joining(", "));
	}

	private static String schema(DbConnection connection) {
		return OracleMaskExpression.quote(connection.schema().toUpperCase(Locale.ROOT));
	}

	private static void execute(DbConnection target, String sql) {
		Properties properties = new Properties();
		properties.setProperty("user", target.username());
		properties.setProperty("password", target.password());

		try (Connection connection = DriverManager.getConnection(target.url(), properties);
				Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(TIMEOUT_SECONDS);
			statement.execute(sql);
		}
		catch (SQLException e) {
			// SQL 본문에는 컬럼명만 들어가고 데이터는 없습니다. 사유를 그대로 전해야 고칠 수 있습니다.
			throw new IllegalStateException(e.getMessage());
		}
	}
}
