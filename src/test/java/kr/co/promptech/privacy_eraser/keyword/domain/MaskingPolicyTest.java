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
}
