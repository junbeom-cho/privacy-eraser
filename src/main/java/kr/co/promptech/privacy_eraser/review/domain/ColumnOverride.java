package kr.co.promptech.privacy_eraser.review.domain;

import lombok.Getter;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;

import java.util.Locale;
import java.util.Objects;

/**
 * 사용자가 검수 화면에서 직접 정한 컬럼 결정입니다. 키워드 판정보다 우선합니다.
 * <p>
 * 엔티티라 클래스입니다. 테이블·컬럼명은 Oracle 관례에 맞춰 대문자로 맞춥니다.
 */
@Getter
public class ColumnOverride {

	private final Long id;
	private final Long projectId;
	private final String tableName;
	private final String columnName;
	private boolean masked;
	private MaskingPolicy policy;

	public ColumnOverride(Long id, Long projectId, String tableName, String columnName,
			boolean masked, MaskingPolicy policy) {
		if (projectId == null) {
			throw new IllegalArgumentException("어느 프로젝트의 결정인지 필요합니다.");
		}
		if (tableName == null || tableName.isBlank()) {
			throw new IllegalArgumentException("테이블명이 필요합니다.");
		}
		if (columnName == null || columnName.isBlank()) {
			throw new IllegalArgumentException("컬럼명이 필요합니다.");
		}
		this.id = id;
		this.projectId = projectId;
		this.tableName = tableName.strip().toUpperCase(Locale.ROOT);
		this.columnName = columnName.strip().toUpperCase(Locale.ROOT);
		assign(masked, policy);
	}

	public static ColumnOverride of(Long projectId, String tableName, String columnName,
			boolean masked, MaskingPolicy policy) {
		return new ColumnOverride(null, projectId, tableName, columnName, masked, policy);
	}

	public void change(boolean masked, MaskingPolicy policy) {
		assign(masked, policy);
	}

	private void assign(boolean masked, MaskingPolicy policy) {
		if (masked && policy == null) {
			throw new IllegalArgumentException("마스킹 대상에는 정책이 필요합니다.");
		}
		this.masked = masked;
		// 마스킹하지 않기로 했으면 정책은 의미가 없습니다. 남겨두면 나중에 잘못 읽힙니다.
		this.policy = masked ? policy : null;
	}

	public MaskingDecision toDecision() {
		return MaskingDecision.byUser(masked, policy);
	}

	public String key() {
		return tableName + "." + columnName;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ColumnOverride override) || id == null || override.id == null) {
			return false;
		}
		return id.equals(override.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "ColumnOverride[id=%s, %s, masked=%s]".formatted(id, key(), masked);
	}
}
