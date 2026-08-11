package kr.co.promptech.privacy_eraser.review.domain;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;

import java.util.Locale;

/**
 * 컬럼 정의서 한 줄입니다. 어느 테이블의 어느 컬럼을 어떻게 할지 사람이 이미 정해둔 값입니다.
 * <p>
 * 키워드는 이름 규칙이라 의도한 것보다 항상 더 많이 걸립니다. 이건 <b>딱 그 조합만</b> 가리킵니다.
 *
 * @param policy 마스킹 대상이 아니면 null
 */
public record ColumnDecision(String tableName, String columnName, boolean masked, MaskingPolicy policy) {

	public ColumnDecision {
		if (tableName == null || tableName.isBlank()) {
			throw new IllegalArgumentException("테이블명이 필요합니다.");
		}
		if (columnName == null || columnName.isBlank()) {
			throw new IllegalArgumentException("컬럼명이 필요합니다.");
		}
		if (masked && policy == null) {
			throw new IllegalArgumentException("마스킹 대상에는 정책이 필요합니다.");
		}
		// 오라클 식별자는 대문자 기준입니다.
		tableName = tableName.strip().toUpperCase(Locale.ROOT);
		columnName = columnName.strip().toUpperCase(Locale.ROOT);
		policy = masked ? policy : null;
	}
}
