package kr.co.promptech.privacy_eraser.schema.domain;

import java.util.List;

public record TableMetadata(String name, List<ColumnMetadata> columns) {

	public TableMetadata {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("테이블명은 비워둘 수 없습니다.");
		}
		name = name.strip();
		columns = columns == null ? List.of() : List.copyOf(columns);
	}
}
