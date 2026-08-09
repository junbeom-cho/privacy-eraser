package kr.co.promptech.privacy_eraser.review.domain;

import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;

/**
 * 검수 화면 한 줄입니다. 컬럼 정보와 판정 결과를 함께 봅니다.
 */
public record ColumnReview(String tableName, ColumnMetadata column, MaskingDecision decision) {

	/**
	 * 정책이 컬럼 길이보다 길면 값 전체가 가려질 수 있습니다. 검수 화면에서 미리 알립니다.
	 */
	public boolean policyExceedsColumnLength() {
		Integer maxLength = column.maxLength();
		return decision.masked() && maxLength != null && decision.policy().length() > maxLength;
	}
}
