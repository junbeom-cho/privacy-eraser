package kr.co.promptech.privacy_eraser.keyword.domain;

import lombok.Getter;

import java.util.Locale;
import java.util.Objects;

/**
 * 프로젝트에 속한 키워드 하나입니다. 컬럼명을 `_` 로 나눈 토큰과 대조해 마스킹 대상을 정합니다.
 * <p>
 * 엔티티라 클래스입니다. 키워드는 프로젝트마다 따로 관리하며, 키워드 하나에 정책 하나입니다.
 */
@Getter
public class Keyword {

	private final Long id;
	private final Long projectId;
	private String word;
	private KeywordType type;
	private MaskingPolicy policy;

	public Keyword(Long id, Long projectId, String word, KeywordType type, MaskingPolicy policy) {
		if (projectId == null) {
			throw new IllegalArgumentException("어느 프로젝트의 키워드인지 필요합니다.");
		}
		this.id = id;
		this.projectId = projectId;
		assign(word, type, policy);
	}

	/** 마스킹 대상으로 삼는 키워드입니다. */
	public static Keyword markFor(Long projectId, String word, MaskingPolicy policy) {
		return new Keyword(null, projectId, word, KeywordType.DO, policy);
	}

	/** 마스킹에서 제외하는 키워드입니다. */
	public static Keyword skipFor(Long projectId, String word) {
		return new Keyword(null, projectId, word, KeywordType.UNDO, null);
	}

	public void update(String word, KeywordType type, MaskingPolicy policy) {
		assign(word, type, policy);
	}

	private void assign(String word, KeywordType type, MaskingPolicy policy) {
		if (word == null || word.isBlank()) {
			throw new IllegalArgumentException("키워드는 비워둘 수 없습니다.");
		}
		if (type == null) {
			throw new IllegalArgumentException("키워드 종류가 필요합니다.");
		}
		if (type == KeywordType.DO && policy == null) {
			throw new IllegalArgumentException("Do 키워드에는 마스킹 정책이 필요합니다.");
		}
		if (type == KeywordType.UNDO && policy != null) {
			throw new IllegalArgumentException("Undo 키워드에는 마스킹 정책을 둘 수 없습니다. 제외가 전부입니다.");
		}
		// 컬럼 토큰이 소문자로 정규화되므로 키워드도 맞춥니다.
		this.word = word.strip().toLowerCase(Locale.ROOT);
		this.type = type;
		this.policy = policy;
	}

	public boolean belongsTo(Long projectId) {
		return this.projectId.equals(projectId);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof Keyword keyword) || id == null || keyword.id == null) {
			return false;
		}
		return id.equals(keyword.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "Keyword[id=%s, word=%s, type=%s]".formatted(id, word, type);
	}
}
