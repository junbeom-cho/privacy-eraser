package kr.co.promptech.privacy_eraser.keyword.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제로 값을 가리는 규칙입니다. 이 도구가 하는 일의 핵심이라 경계를 촘촘히 잡아둡니다.
 */
class MaskingPolicyMaskTest {

	private static MaskingPolicy fromEnd(int length) {
		return MaskingPolicy.partial(MaskingDirection.FROM_END, length);
	}

	private static MaskingPolicy fromStart(int length) {
		return MaskingPolicy.partial(MaskingDirection.FROM_START, length);
	}

	@Test
	void 뒤에서부터_지정한_수만큼_가린다() {
		assertThat(fromEnd(4).mask("01012345678")).isEqualTo("0101234****");
	}

	@Test
	void 앞에서부터_지정한_수만큼_가린다() {
		assertThat(fromStart(1).mask("홍길동")).isEqualTo("*길동");
	}

	@Test
	void 값이_정책보다_짧으면_전체를_가린다() {
		// 남기는 쪽이 아니라 가리는 쪽으로 기웁니다. 개인정보 도구에서 덜 가리는 실수가 더 위험합니다.
		assertThat(fromEnd(10).mask("abc")).isEqualTo("***");
		assertThat(fromStart(10).mask("abc")).isEqualTo("***");
	}

	@Test
	void 길이가_정확히_같으면_전체를_가린다() {
		assertThat(fromEnd(3).mask("abc")).isEqualTo("***");
	}

	@Test
	void NULL_은_그대로_둔다() {
		assertThat(fromEnd(4).mask(null)).isNull();
	}

	@Test
	void 빈_문자열은_그대로_둔다() {
		assertThat(fromEnd(4).mask("")).isEmpty();
	}

	@Test
	void 한_글자만_남기는_경우() {
		assertThat(fromEnd(2).mask("abc")).isEqualTo("a**");
		assertThat(fromStart(2).mask("abc")).isEqualTo("**c");
	}

	@Test
	void 원본_길이는_유지된다() {
		assertThat(fromEnd(4).mask("01012345678")).hasSameSizeAs("01012345678");
	}

	@Test
	void 값이_통째로_가려지는지_알려준다() {
		assertThat(fromEnd(4).masksEntireValue("abc")).isTrue();
		// 길이가 같아도 남는 글자가 없으므로 전체가 가려집니다.
		assertThat(fromEnd(4).masksEntireValue("abcd")).isTrue();
		assertThat(fromEnd(4).masksEntireValue("abcde")).isFalse();
		assertThat(fromEnd(4).masksEntireValue(null)).isFalse();
	}
}
