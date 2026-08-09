package kr.co.promptech.privacy_eraser.review.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.review.domain.ColumnOverride;

public record ColumnOverrideRow(Long overrideId, Long projectId, String tableName, String columnName,
		boolean masked, String maskDirection, Integer maskLength) {

	ColumnOverride toDomain() {
		return new ColumnOverride(overrideId, projectId, tableName, columnName, masked,
				maskDirection == null ? null
						: new MaskingPolicy(MaskingDirection.valueOf(maskDirection), maskLength));
	}
}
