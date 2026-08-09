package kr.co.promptech.privacy_eraser.migration.domain;

import java.util.List;

/**
 * 원본에서 읽은 제약조건 하나입니다. 무엇을 만들지만 담고, SQL 로 옮기는 일은 infrastructure 가 합니다.
 *
 * @param checkExpression     CHECK 일 때만 채워집니다.
 * @param referencedTable     FOREIGN_KEY 일 때만 채워집니다.
 */
public record ConstraintDefinition(String tableName, String name, ConstraintType type, List<String> columns,
		String checkExpression, String referencedTable, List<String> referencedColumns, String deleteRule) {

	public ConstraintDefinition {
		columns = columns == null ? List.of() : List.copyOf(columns);
		referencedColumns = referencedColumns == null ? List.of() : List.copyOf(referencedColumns);
	}

	/** 값이 겹치면 만들 수 없는 종류입니다. 마스킹과 부딪힙니다. */
	public boolean requiresUniqueValues() {
		return type == ConstraintType.PRIMARY_KEY || type == ConstraintType.UNIQUE;
	}
}
