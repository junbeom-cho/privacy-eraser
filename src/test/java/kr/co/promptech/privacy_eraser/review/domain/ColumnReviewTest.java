package kr.co.promptech.privacy_eraser.review.domain;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검수 화면 한 줄입니다. 경고는 전부 <b>부분 마스킹</b> 기준입니다 —
 * 해시는 값 길이와 무관하게 항상 같은 길이가 나옵니다.
 */
class ColumnReviewTest {

	private static ColumnReview review(MaskingPolicy policy, String sample) {
		ColumnMetadata column = ColumnMetadata.character("EMAIL", "VARCHAR2", 5, true);
		return new ColumnReview("EMPLOYEES", column,
				policy == null ? MaskingDecision.notMasked() : MaskingDecision.byDoKeyword("email", policy),
				sample);
	}

	@Test
	void 부분_마스킹은_정책이_컬럼보다_길면_알린다() {
		assertThat(review(MaskingPolicy.partial(MaskingDirection.FROM_END, 9), "abc").policyExceedsColumnLength())
				.isTrue();
	}

	@Test
	void 해시는_길이_초과_경고_대상이_아니다() {
		// 해시에는 자릿수가 없습니다. 예전에는 여기서 NullPointerException 이 났습니다.
		assertThat(review(MaskingPolicy.hash(), "abc").policyExceedsColumnLength()).isFalse();
	}

	@Test
	void 해시는_표본이_통째로_가려지는_경고도_없다() {
		assertThat(review(MaskingPolicy.hash(), "abc").sampleFullyMasked()).isFalse();
	}

	@Test
	void 해시는_미리보기를_보여주지_않는다() {
		// 솔트가 이관 시점에 정해지기 때문입니다.
		assertThat(review(MaskingPolicy.hash(), "abc").maskedSample()).isNull();
	}

	@Test
	void 고정값이_컬럼보다_길면_알린다() {
		// 컬럼이 VARCHAR2(5) 인데 고정값이 11자면 적재 중 ORA-12899 로 실패합니다.
		assertThat(review(MaskingPolicy.fixed("01011111111"), "abc").policyExceedsColumnLength()).isTrue();
	}

	@Test
	void 고정값이_컬럼_안에_들어가면_경고하지_않는다() {
		assertThat(review(MaskingPolicy.fixed("010"), "abc").policyExceedsColumnLength()).isFalse();
	}

	@Test
	void 고정값은_형식과_무관하게_같은_결과를_미리_보여준다() {
		assertThat(review(MaskingPolicy.fixed("010"), "02-123-4567").maskedSample()).isEqualTo("010");
	}
}
