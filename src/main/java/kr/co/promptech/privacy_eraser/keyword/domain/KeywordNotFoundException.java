package kr.co.promptech.privacy_eraser.keyword.domain;

public class KeywordNotFoundException extends RuntimeException {

	public KeywordNotFoundException(Long id) {
		super("키워드를 찾을 수 없습니다. (id: %d)".formatted(id));
	}
}
