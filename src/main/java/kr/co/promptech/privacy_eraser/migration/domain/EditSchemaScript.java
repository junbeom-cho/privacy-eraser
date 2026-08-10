package kr.co.promptech.privacy_eraser.migration.domain;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 이관 대상 스키마를 만드는 SQL 을 글로 만들어 줍니다. 실행은 하지 않습니다.
 * <p>
 * 도구가 직접 만들지 않는 이유는 {@code CREATE USER} 권한 때문입니다. 그 권한을 가진 계정을
 * 앱에 저장하면, 이관이 잘못됐을 때 피해가 이관 대상 스키마를 넘어 DB 전체로 번집니다.
 * 지금 이 도구는 원본에 SELECT, 이관 대상에 소유자 권한만 있으면 됩니다.
 * <p>
 * 대신 <b>무엇이 필요한지</b>는 알려줍니다. 쿼터나 SELECT 권한이 빠지면 이관 도중에야
 * {@code ORA-01950} / {@code ORA-00942} 로 실패하는데, 그때는 이미 시간을 다 쓴 뒤입니다.
 */
public final class EditSchemaScript {

	/** 저장된 접속 비밀번호는 어떤 응답에도 나가지 않습니다. 스크립트도 응답입니다. */
	private static final String PASSWORD_PLACEHOLDER = "<비밀번호>";

	private EditSchemaScript() {
	}

	/**
	 * @param tableNames 원본 테이블 이름. SELECT 권한을 이 목록만큼 줍니다.
	 * @return 실행할 SQL. 이관 대상 접속 정보가 없으면 빈 문자열
	 */
	public static String of(DbConnection raw, DbConnection edit, List<String> tableNames) {
		if (edit == null) {
			return "";
		}
		String schema = quote(edit.schema());
		StringBuilder script = new StringBuilder("""
				-- 이관 대상 스키마를 만드는 SQL 입니다. 도구는 실행하지 않습니다.
				-- 비밀번호는 프로젝트에 등록한 값과 같아야 합니다. 다르면 접속 단계에서 ORA-01017 이 납니다.

				-- [1] DBA 계정(SYSTEM 등)으로 실행합니다.
				CREATE USER %s IDENTIFIED BY "%s";
				GRANT CONNECT, RESOURCE TO %s;
				-- 기본 테이블스페이스가 무엇이든 공간이 모자라지 않게 합니다. 빠뜨리면 적재 중에 ORA-01950 입니다.
				GRANT UNLIMITED TABLESPACE TO %s;
				""".formatted(schema, PASSWORD_PLACEHOLDER, schema, schema));

		if (!edit.username().toUpperCase(Locale.ROOT).equals(edit.schema())) {
			script.append("""

					-- 등록된 접속 계정(%s)과 스키마명(%s)이 다릅니다.
					-- 그 계정이 이 스키마에 테이블을 만들 수 있는지 직접 확인하세요.
					""".formatted(edit.username().toUpperCase(Locale.ROOT), edit.schema()));
		}

		if (!tableNames.isEmpty()) {
			// 객체 소유자는 자기 객체 권한을 줄 수 있습니다. 여기에는 DBA 가 필요 없습니다.
			script.append("""

					-- [2] 원본 계정(%s)으로 실행합니다. DBA 권한이 필요 없습니다.
					-- 이관은 CREATE TABLE ... AS SELECT 를 이관 대상 계정으로 실행하므로,
					-- 그 계정이 원본을 읽을 수 있어야 합니다.
					%s
					""".formatted(raw.username(), tableNames.stream()
							.map(table -> "GRANT SELECT ON %s.%s TO %s;".formatted(
									quote(raw.schema()), quote(table), schema))
							.collect(Collectors.joining("\n"))));
		}
		return script.toString();
	}

	/** 소문자나 예약어가 들어와도 그대로 쓰이도록 감쌉니다. */
	private static String quote(String identifier) {
		return "\"" + identifier.toUpperCase(Locale.ROOT).replace("\"", "") + "\"";
	}
}
