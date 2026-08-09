package kr.co.promptech.privacy_eraser.keyword.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeywordTest {

	private static final MaskingPolicy 뒤_4자리 = new MaskingPolicy(MaskingDirection.FROM_END, 4);

	@Test
	void Do_키워드는_정책과_함께_만든다() {
		Keyword keyword = Keyword.markFor(1L, "phone", 뒤_4자리);

		assertThat(keyword.getWord()).isEqualTo("phone");
		assertThat(keyword.getType()).isEqualTo(KeywordType.DO);
		assertThat(keyword.getPolicy()).isEqualTo(뒤_4자리);
	}

	@Test
	void Do_키워드에_정책이_없으면_만들_수_없다() {
		assertThatThrownBy(() -> Keyword.markFor(1L, "phone", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("정책");
	}

	@Test
	void Undo_키워드는_정책이_없다() {
		Keyword keyword = Keyword.skipFor(1L, "id");

		assertThat(keyword.getType()).isEqualTo(KeywordType.UNDO);
		assertThat(keyword.getPolicy()).isNull();
	}

	@Test
	void Undo_키워드에_정책을_주면_만들_수_없다() {
		assertThatThrownBy(() -> new Keyword(null, 1L, "id", KeywordType.UNDO, 뒤_4자리))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("정책");
	}

	@Test
	void 컬럼_토큰이_소문자라_키워드도_소문자로_맞춘다() {
		assertThat(Keyword.markFor(1L, "  PHONE  ", 뒤_4자리).getWord()).isEqualTo("phone");
	}

	@Test
	void 키워드가_비어있으면_만들_수_없다() {
		assertThatThrownBy(() -> Keyword.skipFor(1L, " "))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 프로젝트가_없으면_만들_수_없다() {
		assertThatThrownBy(() -> Keyword.skipFor(null, "id"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 수정하면_종류와_정책이_함께_바뀐다() {
		Keyword keyword = Keyword.markFor(1L, "phone", 뒤_4자리);

		keyword.update("email", KeywordType.UNDO, null);

		assertThat(keyword.getWord()).isEqualTo("email");
		assertThat(keyword.getType()).isEqualTo(KeywordType.UNDO);
		assertThat(keyword.getPolicy()).isNull();
	}

	@Test
	void 수정할_때도_같은_규칙을_지킨다() {
		Keyword keyword = Keyword.markFor(1L, "phone", 뒤_4자리);

		assertThatThrownBy(() -> keyword.update("phone", KeywordType.DO, null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
