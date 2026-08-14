package kr.co.promptech.privacy_eraser.migration.infrastructure;

import kr.co.promptech.privacy_eraser.migration.domain.CommentDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.ConstraintDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.ConstraintType;
import kr.co.promptech.privacy_eraser.migration.domain.IndexDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationExecutor;
import kr.co.promptech.privacy_eraser.migration.domain.SequenceDefinition;
import kr.co.promptech.privacy_eraser.migration.domain.MigrationTarget;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.security.SecureRandom;
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
@Slf4j
@Component
public class OracleMigrationExecutor implements MigrationExecutor {

	private static final int TIMEOUT_SECONDS = 600;

	/**
	 * 실행 중에 열어 두는 접속입니다. 이관은 스레드 하나에서 순서대로 돌기 때문에 스레드마다 하나면 됩니다.
	 */
	private final ThreadLocal<Connection> session = new ThreadLocal<>();

	/**
	 * 해시에 붙일 솔트입니다. 이관 한 건마다 새로 만들고 <b>끝나면 버립니다.</b>
	 * 어디에도 저장하지 않으므로 아무도 되돌릴 수 없습니다.
	 * <p>
	 * 이관본 안에서는 모든 해시가 같은 솔트로 만들어져 FK 가 그대로 맞습니다.
	 * 재이관하면 값이 전부 달라지지만, 테이블을 전부 다시 만들므로 내부 정합성은 유지됩니다.
	 */
	private final ThreadLocal<String> salt = new ThreadLocal<>();

	private static final SecureRandom SALT_RANDOM = new SecureRandom();
	private static final int SALT_BYTES = 32;

	@Override
	public void openSession(DbConnection edit) {
		closeSession();
		session.set(connect(edit));
		salt.set(newSalt());
	}

	/** 2^256 이라 키 파생 함수로 늘릴 것이 없습니다. 처음부터 강하게 만듭니다. */
	private static String newSalt() {
		byte[] bytes = new byte[SALT_BYTES];
		SALT_RANDOM.nextBytes(bytes);
		StringBuilder hex = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			hex.append("%02x".formatted(b));
		}
		return hex.toString();
	}

	@Override
	public void closeSession() {
		Connection open = session.get();
		session.remove();
		salt.remove();
		if (open != null) {
			try {
				open.close();
			}
			catch (SQLException e) {
				// 닫는 데 실패해도 할 수 있는 일이 없습니다. 이관 결과를 뒤집을 이유는 더더욱 없습니다.
				log.warn("이관 대상 접속을 닫지 못했습니다.", e);
			}
		}
	}

	private static Connection connect(DbConnection target) {
		Properties properties = new Properties();
		properties.setProperty("user", target.username());
		properties.setProperty("password", target.password());
		try {
			return DriverManager.getConnection(target.url(), properties);
		}
		catch (SQLException e) {
			throw new IllegalStateException(e.getMessage());
		}
	}

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
		String runSalt = salt.get();
		String selectList = target.columns().stream()
				.map(column -> "%s AS %s".formatted(
						OracleMaskExpression.of(column.name(), column.policy(), runSalt),
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

	/**
	 * 시퀀스는 테이블에 딸린 객체가 아니라 따로 남습니다. 테이블을 지워도 시퀀스는 그대로라
	 * 먼저 지우지 않으면 재이관할 때마다 ORA-00955 로 실패합니다.
	 */
	@Override
	public void createSequence(DbConnection edit, SequenceDefinition sequence) {
		String name = "%s.%s".formatted(schema(edit), OracleMaskExpression.quote(sequence.name()));
		// 없는 시퀀스를 지우면 ORA-02289 가 납니다. 첫 실행에서는 정상이므로 무시합니다.
		execute(edit, """
				BEGIN
				  EXECUTE IMMEDIATE 'DROP SEQUENCE %s';
				EXCEPTION WHEN OTHERS THEN
				  IF SQLCODE != -2289 THEN RAISE; END IF;
				END;""".formatted(name));
		execute(edit, "CREATE SEQUENCE %s START WITH %d INCREMENT BY %d".formatted(
				name, Math.max(sequence.startWith(), 1), sequence.incrementBy()));
	}

	private static String columnList(java.util.List<String> columns) {
		return columns.stream().map(OracleMaskExpression::quote).collect(Collectors.joining(", "));
	}

	private static String schema(DbConnection connection) {
		return OracleMaskExpression.quote(connection.schema().toUpperCase(Locale.ROOT));
	}

	/** 열어 둔 접속이 있으면 그것을 씁니다. 없으면 이 문장만을 위해 하나 열고 닫습니다. */
	private void execute(DbConnection target, String sql) {
		Connection open = session.get();
		if (open != null) {
			run(open, sql);
			return;
		}
		try (Connection connection = connect(target)) {
			run(connection, sql);
		}
		catch (SQLException e) {
			throw new IllegalStateException(e.getMessage());
		}
	}

	private static void run(Connection connection, String sql) {
		try (Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(TIMEOUT_SECONDS);
			statement.execute(sql);
		}
		catch (SQLException e) {
			// SQL 본문에는 컬럼명만 들어가고 데이터는 없습니다. 사유를 그대로 전해야 고칠 수 있습니다.
			throw new IllegalStateException(e.getMessage());
		}
	}
}
