package kr.co.promptech.privacy_eraser.migration.domain;

import java.util.List;

public record IndexDefinition(String tableName, String name, boolean unique, List<String> columns) {

	public IndexDefinition {
		columns = columns == null ? List.of() : List.copyOf(columns);
	}
}
