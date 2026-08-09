package kr.co.promptech.privacy_eraser.migration.domain;

/**
 * @param columnName null 이면 테이블 코멘트입니다.
 */
public record CommentDefinition(String tableName, String columnName, String comment) {

	public boolean isTableComment() {
		return columnName == null;
	}
}
