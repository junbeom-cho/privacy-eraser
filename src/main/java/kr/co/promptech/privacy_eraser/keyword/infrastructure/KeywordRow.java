package kr.co.promptech.privacy_eraser.keyword.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordType;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingType;

/**
 * keyword 테이블 한 행입니다. 정책이 여러 컬럼으로 흩어져 있어 조회 후 조립합니다.
 */
public record KeywordRow(Long keywordId, Long projectId, String word, String keywordType,
		String maskType, String maskDirection, Integer maskLength) {

	Keyword toDomain() {
		return new Keyword(keywordId, projectId, word, KeywordType.valueOf(keywordType), toPolicy());
	}

	/** 정책이 없는 행(Undo 키워드)은 방식도 없습니다. */
	private MaskingPolicy toPolicy() {
		if (maskType == null) {
			return null;
		}
		return MaskingType.valueOf(maskType) == MaskingType.HASH
				? MaskingPolicy.hash()
				: MaskingPolicy.partial(MaskingDirection.valueOf(maskDirection), maskLength);
	}
}
