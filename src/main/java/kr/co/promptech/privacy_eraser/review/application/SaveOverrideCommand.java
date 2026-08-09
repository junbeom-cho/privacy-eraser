package kr.co.promptech.privacy_eraser.review.application;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;

/**
 * 검수 화면에서 사용자가 컬럼 하나를 직접 정할 때 쓰는 명령입니다.
 * 마스킹하지 않기로 했으면 {@code policy} 는 null 입니다.
 */
public record SaveOverrideCommand(String tableName, String columnName, boolean masked, MaskingPolicy policy) {
}
