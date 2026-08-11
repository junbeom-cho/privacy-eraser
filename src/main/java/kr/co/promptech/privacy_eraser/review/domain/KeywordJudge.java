package kr.co.promptech.privacy_eraser.review.domain;

import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordType;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
		// Undo 가 먼저입니다. Do 에도 걸렸는지는 볼 필요가 없습니다.
		Optional<Keyword> excluded = firstMatch(undoKeywords, column);
		if (excluded.isPresent()) {
			return MaskingDecision.byUndoKeyword(excluded.get().getWord());
		}

		return firstMatch(doKeywords, column)
				.map(keyword -> MaskingDecision.byDoKeyword(keyword.getWord(), keyword.getPolicy()))
				.orElseGet(MaskingDecision::notMasked);
	}

	private static Optional<Keyword> firstMatch(List<Keyword> keywords, ColumnMetadata column) {
		Set<String> tokens = Set.copyOf(column.tokens());
		String fullName = column.name().toLowerCase(Locale.ROOT);
		return keywords.stream().filter(keyword -> matches(keyword, tokens, fullName)).findFirst();
	}

	/**
	 * 토큰 하나와 맞거나, <b>컬럼명 전체</b>와 맞으면 걸립니다.
	 * <p>
	 * `OWNR_BMNO` 같은 이름은 토큰으로 쪼개면 `ownr` 과 `bmno` 라, 컬럼명을 통째로 적어도
	 * 아무것도 안 걸렸습니다. 이름이 겹치는 다른 컬럼까지 딸려오는 것을 피하려면 통째로 지정할 수
	 * 있어야 합니다.
	 * <p>
	 * 부분 일치는 하지 않습니다. `ownr_bm` 이 `OWNR_BMNO` 에 걸리면 의도치 않은 컬럼이 따라옵니다.
	 */
	private static boolean matches(Keyword keyword, Set<String> tokens, String fullName) {
		return tokens.contains(keyword.getWord()) || fullName.equals(keyword.getWord());
	}
}
