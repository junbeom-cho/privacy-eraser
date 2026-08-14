package kr.co.promptech.privacy_eraser.keyword.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaskingPolicyTest {

	@Test
	void 앞에서부터_가리는_정책을_만든다() {
		MaskingPolicy policy = MaskingPolicy.partial(MaskingDirection.FROM_START, 3);

		assertThat(policy.direction()).isEqualTo(MaskingDirection.FROM_START);
		assertThat(policy.length()).isEqualTo(3);
	}

	@Test
	void 마스킹_개수는_1개_이상이어야_한다() {
		assertThatThrownBy(() -> MaskingPolicy.partial(MaskingDirection.FROM_END, 0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MaskingPolicy.partial(MaskingDirection.FROM_END, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 방향이_없으면_만들_수_없다() {
		assertThatThrownBy(() -> MaskingPolicy.partial(null, 3))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ===== 해시 =====

	@Test
	void 해시는_방향과_자릿수가_없다() {
		MaskingPolicy policy = MaskingPolicy.hash();

		assertThat(policy.type()).isEqualTo(MaskingType.HASH);
		assertThat(policy.direction()).isNull();
		assertThat(policy.length()).isNull();
	}

	@Test
	void 부분_마스킹은_방향과_자릿수가_있다() {
		MaskingPolicy policy = MaskingPolicy.partial(MaskingDirection.FROM_END, 4);

		assertThat(policy.type()).isEqualTo(MaskingType.PARTIAL);
		assertThat(policy.direction()).isEqualTo(MaskingDirection.FROM_END);
		assertThat(policy.length()).isEqualTo(4);
	}

	@Test
	void 해시는_미리_보여줄_수_없다() {
		// 솔트가 이관 시점에 정해지므로 검수 화면에서 결과를 계산할 수 없습니다.
		assertThat(MaskingPolicy.hash().mask("880101-1234567")).isNull();
	}

	@Test
	void 해시는_통째로_가려지는_문제와_무관하다() {
		// 값 길이와 상관없이 항상 같은 길이가 나옵니다.
		assertThat(MaskingPolicy.hash().masksEntireValue("짧음")).isFalse();
	}

	@Test
	void 값이_겹칠_수_있는지는_방식이_정한다() {
		assertThat(MaskingPolicy.partial(MaskingDirection.FROM_END, 4).mayCollide()).isTrue();
		assertThat(MaskingPolicy.hash().mayCollide()).isFalse();
	}

	// ===== 고정값 =====

	@Test
	void 고정값은_값을_들고_있고_방향과_자릿수는_없다() {
		MaskingPolicy policy = MaskingPolicy.fixed("01011111111");

		assertThat(policy.type()).isEqualTo(MaskingType.FIXED);
		assertThat(policy.fixedValue()).isEqualTo("01011111111");
		assertThat(policy.direction()).isNull();
		assertThat(policy.length()).isNull();
	}

	@Test
	void 고정값은_형식과_무관하게_같은_결과를_낸다() {
		MaskingPolicy policy = MaskingPolicy.fixed("01011111111");

		assertThat(policy.mask("010-1234-5678")).isEqualTo("01011111111");
		assertThat(policy.mask("01012345678")).isEqualTo("01011111111");
		// 전화번호가 아닌 쓰레기 값도 유효한 모양이 됩니다.
		assertThat(policy.mask("2830451")).isEqualTo("01011111111");
	}

	@Test
	void 고정값도_NULL_은_NULL_로_둔다() {
		// 원래 비어 있던 값을 채우면 없던 데이터가 생깁니다.
		assertThat(MaskingPolicy.fixed("01011111111").mask(null)).isNull();
	}

	@Test
	void 값이_없는_고정값은_만들_수_없다() {
		assertThatThrownBy(() -> MaskingPolicy.fixed("  "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("고정값");
	}

	@Test
	void 고정값은_모든_행이_같아져_PK_에_쓸_수_없다() {
		assertThat(MaskingPolicy.fixed("01011111111").mayCollide()).isTrue();
	}

	@Test
	void 고정값은_길이_초과_경고_대상이_아니다() {
		// 원본 길이를 보지 않습니다. 대신 고정값 자체가 컬럼보다 긴지는 따로 봅니다.
		assertThat(MaskingPolicy.fixed("01011111111").masksEntireValue("짧음")).isFalse();
	}

	@Test
	void 고정값이_컬럼_길이보다_길면_알_수_있다() {
		assertThat(MaskingPolicy.fixed("01011111111").exceeds(5)).isTrue();
		assertThat(MaskingPolicy.fixed("01011111111").exceeds(20)).isFalse();
		// 다른 방식은 이 문제가 없습니다.
		assertThat(MaskingPolicy.hash().exceeds(5)).isFalse();
	}
}
