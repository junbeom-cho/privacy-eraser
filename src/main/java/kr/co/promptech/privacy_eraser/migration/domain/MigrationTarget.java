package kr.co.promptech.privacy_eraser.migration.domain;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;

import java.util.List;

/**
 * 테이블 한 개를 어떻게 옮길지입니다. 도메인이 "어느 컬럼을 어떻게" 까지 정하고,
 * infrastructure 가 이것을 SQL 로 옮깁니다.
 */
public record MigrationTarget(String tableName, List<Column> columns) {

	/**
	 * @param policy null 이면 마스킹하지 않고 그대로 옮깁니다.
	 */
	public record Column(String name, MaskingPolicy policy) {
	}

	public MigrationTarget {
		columns = columns == null ? List.of() : List.copyOf(columns);
	}
}
