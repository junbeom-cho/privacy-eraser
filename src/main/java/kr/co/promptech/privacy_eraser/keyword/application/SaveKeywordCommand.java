package kr.co.promptech.privacy_eraser.keyword.application;

import kr.co.promptech.privacy_eraser.keyword.domain.KeywordType;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;

/**
 * 등록과 수정이 받는 값이 같아 명령을 하나로 씁니다.
 * {@code policy} 는 {@code UNDO} 일 때 null 입니다.
 */
public record SaveKeywordCommand(Long projectId, String word, KeywordType type, MaskingPolicy policy) {
}
