package kr.co.promptech.privacy_eraser.review.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingType;
import kr.co.promptech.privacy_eraser.review.domain.ColumnOverride;

public record ColumnOverrideRow(Long overrideId, Long projectId, String tableName, String columnName,
		boolean masked, String maskType, String maskDirection, Integer maskLength) {

	ColumnOverride toDomain() {
		return new ColumnOverride(overrideId, projectId, tableName, columnName, masked, toPolicy());
	}

	/** 마스킹 대상이 아닌 행은 방식도 없습니다. */
	private MaskingPolicy toPolicy() {
		if (maskType == null) {
			return null;
		}
		return MaskingType.valueOf(maskType) == MaskingType.HASH
				? MaskingPolicy.hash()
				: MaskingPolicy.partial(MaskingDirection.valueOf(maskDirection), maskLength);
	}
}
