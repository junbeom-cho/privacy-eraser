package kr.co.promptech.privacy_eraser.review.domain;

import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordType;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 이 프로젝트의 핵심 규칙입니다. 우선순위는 <b>사용자 지정 &gt; Undo &gt; Do &gt; 비대상</b> 입니다.
 * <p>
 * 판정은 여기 한 곳에서만 합니다. 호출부마다 조건 분기를 두면 규칙이 갈라집니다.
 */
public class KeywordJudge {

	/**
	 * 같은 종류의 키워드가 여럿 걸리면 <b>더 긴 키워드</b>가 이깁니다. 더 구체적인 쪽을 따르되,
	 * 길이가 같으면 사전순으로 정해 결과가 항상 같게 만듭니다.
	 */
	private static final Comparator<Keyword> MORE_SPECIFIC_FIRST =
			Comparator.comparingInt((Keyword keyword) -> keyword.getWord().length()).reversed()
					.thenComparing(Keyword::getWord);

	private final List<Keyword> doKeywords;
	private final List<Keyword> undoKeywords;

	public KeywordJudge(List<Keyword> keywords) {
		List<Keyword> source = keywords == null ? List.of() : keywords;
		this.doKeywords = source.stream()
				.filter(keyword -> keyword.getType() == KeywordType.DO)
				.sorted(MORE_SPECIFIC_FIRST)
				.toList();
		this.undoKeywords = source.stream()
				.filter(keyword -> keyword.getType() == KeywordType.UNDO)
				.sorted(MORE_SPECIFIC_FIRST)
				.toList();
	}

	/**
	 * 사용자가 지정한 값이 있으면 그것이 이깁니다. 없으면 키워드로 판정합니다.
	 *
	 * @param override 사용자 지정. 없으면 null
	 */
	public MaskingDecision judgeWithOverride(ColumnMetadata column, MaskingDecision override) {
		return override != null ? override : judge(column);
	}

	public MaskingDecision judge(ColumnMetadata column) {
		Set<String> tokens = Set.copyOf(column.tokens());

		// Undo 가 먼저입니다. Do 에도 걸렸는지는 볼 필요가 없습니다.
		Optional<Keyword> excluded = firstMatch(undoKeywords, tokens);
		if (excluded.isPresent()) {
			return MaskingDecision.byUndoKeyword(excluded.get().getWord());
		}

		return firstMatch(doKeywords, tokens)
				.map(keyword -> MaskingDecision.byDoKeyword(keyword.getWord(), keyword.getPolicy()))
				.orElseGet(MaskingDecision::notMasked);
	}

	private static Optional<Keyword> firstMatch(List<Keyword> keywords, Set<String> tokens) {
		return keywords.stream().filter(keyword -> tokens.contains(keyword.getWord())).findFirst();
	}
}
