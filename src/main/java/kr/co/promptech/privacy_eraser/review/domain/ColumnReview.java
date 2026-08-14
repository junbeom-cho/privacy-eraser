package kr.co.promptech.privacy_eraser.review.domain;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingType;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;

/**
 * 검수 화면 한 줄입니다. 컬럼 정보와 판정 결과, 그리고 원본 표본 값을 함께 봅니다.
 *
 * @param sample 원본에서 읽은 값 하나. <b>진짜 개인정보</b>라 화면에만 쓰고 저장·로그에 남기지 않습니다.
 */
public record ColumnReview(String tableName, ColumnMetadata column, MaskingDecision decision, String sample) {

	/**
	 * 표본에 정책을 적용한 결과입니다. 마스킹 대상이 아니면 원본 그대로입니다.
	 */
	public String maskedSample() {
		return decision.masked() ? decision.policy().mask(sample) : sample;
	}

	/**
	 * 표본이 통째로 가려지는지 여부입니다. 정책이 값보다 길다는 뜻이라 화면에서 알립니다.
	 */
	public boolean sampleFullyMasked() {
		return decision.masked() && decision.policy().masksEntireValue(sample);
	}

	/**
	 * 정책이 컬럼 최대 길이보다 길면 그 컬럼의 값은 대부분 통째로 가려집니다.
	 */
	public boolean policyExceedsColumnLength() {
		Integer maxLength = column.maxLength();
		// 해시에는 자릿수가 없습니다. 값 길이와 무관하게 항상 같은 길이가 나옵니다.
		return decision.masked() && maxLength != null
				&& decision.policy().type() == MaskingType.PARTIAL
				&& decision.policy().length() > maxLength;
	}
}
