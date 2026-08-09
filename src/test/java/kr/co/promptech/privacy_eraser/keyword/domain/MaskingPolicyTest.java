package kr.co.promptech.privacy_eraser.keyword.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaskingPolicyTest {

	@Test
	void 앞에서부터_가리는_정책을_만든다() {
		MaskingPolicy policy = new MaskingPolicy(MaskingDirection.FROM_START, 3);

		assertThat(policy.direction()).isEqualTo(MaskingDirection.FROM_START);
		assertThat(policy.length()).isEqualTo(3);
	}

	@Test
	void 마스킹_개수는_1개_이상이어야_한다() {
		assertThatThrownBy(() -> new MaskingPolicy(MaskingDirection.FROM_END, 0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MaskingPolicy(MaskingDirection.FROM_END, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 방향이_없으면_만들_수_없다() {
		assertThatThrownBy(() -> new MaskingPolicy(null, 3))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
