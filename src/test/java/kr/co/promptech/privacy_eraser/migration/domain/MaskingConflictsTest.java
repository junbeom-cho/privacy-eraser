package kr.co.promptech.privacy_eraser.migration.domain;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마스킹된 값은 서로 겹칠 수 있어 PK·UNIQUE 를 만들 수 없습니다.
 * 데이터를 다 옮긴 뒤에 알면 늦으므로 시작 전에 걸러냅니다.
 */
class MaskingConflictsTest {

	private static final MaskingPolicy 뒤_4자리 = new MaskingPolicy(MaskingDirection.FROM_END, 4);

	private static ConstraintDefinition unique(String table, String... columns) {
		return new ConstraintDefinition(table, "UK_" + table, ConstraintType.UNIQUE,
				List.of(columns), null, null, null, null);
	}

	private static ConstraintDefinition foreignKey(String table, String column) {
		return new ConstraintDefinition(table, "FK_" + table, ConstraintType.FOREIGN_KEY,
				List.of(column), null, "OTHER", List.of("ID"), "NO ACTION");
	}

	private static MigrationTarget target(String table, String maskedColumn) {
		return new MigrationTarget(table, List.of(
				new MigrationTarget.Column("ID", null),
				new MigrationTarget.Column(maskedColumn, 뒤_4자리)));
	}

	@Test
	void 마스킹된_컬럼에_UNIQUE_가_걸려_있으면_충돌이다() {
		List<String> conflicts = MaskingConflicts.find(
				List.of(unique("EMPLOYEES", "EMAIL")),
				List.of(target("EMPLOYEES", "EMAIL")));

		assertThat(conflicts).hasSize(1);
		assertThat(conflicts.get(0)).contains("EMPLOYEES").contains("EMAIL").contains("UK_EMPLOYEES");
	}

	@Test
	void 마스킹하지_않는_컬럼의_UNIQUE_는_괜찮다() {
		List<String> conflicts = MaskingConflicts.find(
				List.of(unique("EMPLOYEES", "ID")),
				List.of(target("EMPLOYEES", "EMAIL")));

		assertThat(conflicts).isEmpty();
	}

	@Test
	void PK_도_같은_이유로_충돌이다() {
		ConstraintDefinition pk = new ConstraintDefinition("EMPLOYEES", "PK_EMP", ConstraintType.PRIMARY_KEY,
				List.of("EMAIL"), null, null, null, null);

		assertThat(MaskingConflicts.find(List.of(pk), List.of(target("EMPLOYEES", "EMAIL")))).hasSize(1);
	}

	@Test
	void FK_와_CHECK_는_값_중복과_무관해_여기서_보지_않는다() {
		// 실패할 수 있지만 미리 알 수 없습니다. 실행 중에 드러납니다.
		List<ConstraintDefinition> constraints = List.of(
				foreignKey("EMPLOYEES", "EMAIL"),
				new ConstraintDefinition("EMPLOYEES", "CK", ConstraintType.CHECK,
						List.of("EMAIL"), "email LIKE '%@%'", null, null, null));

		assertThat(MaskingConflicts.find(constraints, List.of(target("EMPLOYEES", "EMAIL")))).isEmpty();
	}

	@Test
	void 복합키는_한_컬럼만_마스킹돼도_충돌이다() {
		assertThat(MaskingConflicts.find(
				List.of(unique("EMPLOYEES", "ID", "EMAIL")),
				List.of(target("EMPLOYEES", "EMAIL")))).hasSize(1);
	}

	@Test
	void 대소문자는_구분하지_않는다() {
		assertThat(MaskingConflicts.find(
				List.of(unique("employees", "email")),
				List.of(target("EMPLOYEES", "EMAIL")))).hasSize(1);
	}

	@Test
	void 이관_대상에_없는_테이블의_제약조건은_무시한다() {
		assertThat(MaskingConflicts.find(
				List.of(unique("OTHER_TABLE", "EMAIL")),
				List.of(target("EMPLOYEES", "EMAIL")))).isEmpty();
	}
}
