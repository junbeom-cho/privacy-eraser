package kr.co.promptech.privacy_eraser.keyword.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordType;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;

/**
 * keyword 테이블 한 행입니다. 정책이 두 컬럼으로 흩어져 있어 조회 후 조립합니다.
 */
public record KeywordRow(Long keywordId, Long projectId, String word, String keywordType,
		String maskDirection, Integer maskLength) {

	Keyword toDomain() {
		return new Keyword(keywordId, projectId, word, KeywordType.valueOf(keywordType),
				maskDirection == null ? null
						: new MaskingPolicy(MaskingDirection.valueOf(maskDirection), maskLength));
	}
}
