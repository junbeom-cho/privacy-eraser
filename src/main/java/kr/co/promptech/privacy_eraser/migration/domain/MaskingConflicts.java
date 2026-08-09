package kr.co.promptech.privacy_eraser.migration.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 마스킹된 값은 서로 겹칠 수 있어 PK·UNIQUE 를 만들 수 없습니다.
 * 데이터를 다 옮긴 뒤에 알면 늦으므로 시작 전에 걸러냅니다.
 * <p>
 * FK·CHECK 도 실패할 수 있지만 값을 봐야 알 수 있어 여기서 다루지 않습니다. 실행 중에 드러납니다.
 */
public final class MaskingConflicts {

	private MaskingConflicts() {
	}

	/**
	 * @return 사람이 읽을 수 있는 충돌 설명. 비어 있으면 시작해도 됩니다.
	 */
	public static List<String> find(List<ConstraintDefinition> constraints, List<MigrationTarget> targets) {
		Set<String> maskedColumns = targets.stream()
				.flatMap(target -> target.columns().stream()
						.filter(column -> column.policy() != null)
						.map(column -> key(target.tableName(), column.name())))
				.collect(Collectors.toSet());

		Set<String> migratedTables = targets.stream()
				.map(target -> target.tableName().toUpperCase(Locale.ROOT))
				.collect(Collectors.toSet());

		return constraints.stream()
				.filter(ConstraintDefinition::requiresUniqueValues)
				.filter(constraint -> migratedTables.contains(constraint.tableName().toUpperCase(Locale.ROOT)))
				.filter(constraint -> constraint.columns().stream()
						.anyMatch(column -> maskedColumns.contains(key(constraint.tableName(), column))))
				.map(MaskingConflicts::describe)
				.toList();
	}

	private static String describe(ConstraintDefinition constraint) {
		return "%s.%s 에 %s(%s)가 걸려 있어 마스킹하면 값이 겹칩니다. 비대상으로 바꾸거나 정책을 조정하세요."
				.formatted(constraint.tableName(), String.join(", ", constraint.columns()),
						constraint.name(), constraint.type() == ConstraintType.PRIMARY_KEY ? "기본키" : "고유키");
	}

	private static String key(String tableName, String columnName) {
		return (tableName + "." + columnName).toUpperCase(Locale.ROOT);
	}
}
