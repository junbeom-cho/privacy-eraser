package kr.co.promptech.privacy_eraser.review.domain;

import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 프로젝트의 핵심 규칙입니다. 우선순위는 사용자 지정 &gt; Undo &gt; Do &gt; 비대상 입니다.
 */
class KeywordJudgeTest {

	private static final MaskingPolicy 뒤_4자리 = new MaskingPolicy(MaskingDirection.FROM_END, 4);
	private static final MaskingPolicy 앞_1자리 = new MaskingPolicy(MaskingDirection.FROM_START, 1);

	private static ColumnMetadata column(String name) {
		return ColumnMetadata.character(name, "VARCHAR2", 50, true);
	}

	private static MaskingDecision judge(ColumnMetadata column, Keyword... keywords) {
		return new KeywordJudge(List.of(keywords)).judge(column);
	}

	@Test
	void 어느_키워드에도_안_걸리면_비대상이다() {
		MaskingDecision decision = judge(column("SALARY"), Keyword.markFor(1L, "phone", 뒤_4자리));

		assertThat(decision.masked()).isFalse();
		assertThat(decision.source()).isEqualTo(DecisionSource.NO_MATCH);
		assertThat(decision.policy()).isNull();
		assertThat(decision.matchedKeyword()).isNull();
	}

	@Test
	void Do_키워드에_걸리면_대상이고_그_정책을_따른다() {
		MaskingDecision decision = judge(column("PHONE_NUMBER"), Keyword.markFor(1L, "phone", 뒤_4자리));

		assertThat(decision.masked()).isTrue();
		assertThat(decision.source()).isEqualTo(DecisionSource.DO_KEYWORD);
		assertThat(decision.policy()).isEqualTo(뒤_4자리);
		assertThat(decision.matchedKeyword()).isEqualTo("phone");
	}

	@Test
	void Undo_키워드에_걸리면_제외다() {
		MaskingDecision decision = judge(column("EMPLOYEE_ID"), Keyword.skipFor(1L, "id"));

		assertThat(decision.masked()).isFalse();
		assertThat(decision.source()).isEqualTo(DecisionSource.UNDO_KEYWORD);
		assertThat(decision.matchedKeyword()).isEqualTo("id");
	}

	@Test
	void Do와_Undo에_함께_걸리면_Undo가_이긴다() {
		MaskingDecision decision = judge(column("MANAGER_ID"),
				Keyword.markFor(1L, "manager", 뒤_4자리),
				Keyword.skipFor(1L, "id"));

		assertThat(decision.masked()).isFalse();
		assertThat(decision.source()).isEqualTo(DecisionSource.UNDO_KEYWORD);
		assertThat(decision.matchedKeyword()).isEqualTo("id");
	}

	@Test
	void 토큰은_완전히_같아야_걸린다() {
		// 부분 일치를 허용하면 `id` 가 `paid`·`identity` 에도 걸려 사고가 납니다.
		MaskingDecision decision = judge(column("TELEPHONE"), Keyword.markFor(1L, "phone", 뒤_4자리));

		assertThat(decision.masked()).isFalse();
	}

	@Test
	void 대소문자는_구분하지_않는다() {
		assertThat(judge(column("Phone_Number"), Keyword.markFor(1L, "PHONE", 뒤_4자리)).masked()).isTrue();
	}

	@Test
	void Do가_여러_개_걸리면_더_긴_키워드가_이긴다() {
		// 더 구체적인 키워드를 따릅니다. 결과가 항상 같아야 하므로 규칙을 고정합니다.
		MaskingDecision decision = judge(column("ACCOUNT_NUMBER"),
				Keyword.markFor(1L, "number", 뒤_4자리),
				Keyword.markFor(1L, "account", 앞_1자리));

		assertThat(decision.matchedKeyword()).isEqualTo("account");
		assertThat(decision.policy()).isEqualTo(앞_1자리);
	}

	@Test
	void 길이가_같으면_사전순으로_정한다() {
		MaskingDecision decision = judge(column("EMAIL_PHONE"),
				Keyword.markFor(1L, "phone", 뒤_4자리),
				Keyword.markFor(1L, "email", 앞_1자리));

		assertThat(decision.matchedKeyword()).isEqualTo("email");
	}

	@Test
	void 키워드가_하나도_없으면_전부_비대상이다() {
		assertThat(new KeywordJudge(List.of()).judge(column("PHONE_NUMBER")).masked()).isFalse();
	}

	@Test
	void 사용자가_지정하면_키워드_판정을_덮어쓴다() {
		KeywordJudge judge = new KeywordJudge(List.of(Keyword.skipFor(1L, "id")));

		MaskingDecision decision = judge.judgeWithOverride(column("EMPLOYEE_ID"), MaskingDecision.byUser(true, 앞_1자리));

		assertThat(decision.masked()).isTrue();
		assertThat(decision.source()).isEqualTo(DecisionSource.USER);
		assertThat(decision.policy()).isEqualTo(앞_1자리);
	}

	@Test
	void 사용자_지정이_없으면_키워드_판정을_쓴다() {
		KeywordJudge judge = new KeywordJudge(List.of(Keyword.markFor(1L, "phone", 뒤_4자리)));

		MaskingDecision decision = judge.judgeWithOverride(column("PHONE_NUMBER"), null);

		assertThat(decision.source()).isEqualTo(DecisionSource.DO_KEYWORD);
	}

	// ===== 컬럼명 전체로 지정 =====

	@Test
	void 컬럼명_전체를_키워드로_넣으면_그_컬럼이_걸린다() {
		// 토큰으로 쪼개면 ownr, bmno 라 컬럼명 전체는 지금까지 아무것도 안 걸렸습니다.
		KeywordJudge judge = new KeywordJudge(List.of(Keyword.markFor(1L, "OWNR_BMNO", 뒤_4자리)));

		MaskingDecision decision = judge.judge(column("OWNR_BMNO"));

		assertThat(decision.masked()).isTrue();
		assertThat(decision.matchedKeyword()).isEqualTo("ownr_bmno");
	}

	@Test
	void 컬럼명_전체는_다른_컬럼에_걸리지_않는다() {
		KeywordJudge judge = new KeywordJudge(List.of(Keyword.markFor(1L, "OWNR_BMNO", 뒤_4자리)));

		assertThat(judge.judge(column("NEW_BMNO")).masked()).isFalse();
		assertThat(judge.judge(column("OWNR_NM")).masked()).isFalse();
	}

	@Test
	void 컬럼명_일부만_적으면_걸리지_않는다() {
		// 토큰 경계를 무시하고 부분 일치시키면 의도치 않은 컬럼이 딸려옵니다.
		KeywordJudge judge = new KeywordJudge(List.of(Keyword.markFor(1L, "ownr_bm", 뒤_4자리)));

		assertThat(judge.judge(column("OWNR_BMNO")).masked()).isFalse();
	}

	@Test
	void 컬럼명_전체가_토큰보다_우선한다() {
		// 더 구체적인 쪽이 이깁니다. 길이 비교로 자연히 그렇게 됩니다.
		KeywordJudge judge = new KeywordJudge(List.of(Keyword.markFor(1L, "bmno", 뒤_4자리), Keyword.markFor(1L, "OWNR_BMNO", 뒤_4자리)));

		assertThat(judge.judge(column("OWNR_BMNO")).matchedKeyword()).isEqualTo("ownr_bmno");
	}

	@Test
	void 컬럼명_전체로도_제외할_수_있다() {
		KeywordJudge judge = new KeywordJudge(List.of(Keyword.markFor(1L, "bmno", 뒤_4자리), Keyword.skipFor(1L, "OWNR_BMNO")));

		MaskingDecision decision = judge.judge(column("OWNR_BMNO"));

		assertThat(decision.masked()).isFalse();
		assertThat(decision.matchedKeyword()).isEqualTo("ownr_bmno");
	}

	@Test
	void 컬럼명_전체는_대소문자를_구분하지_않는다() {
		KeywordJudge judge = new KeywordJudge(List.of(Keyword.markFor(1L, "ownr_bmno", 뒤_4자리)));

		assertThat(judge.judge(column("OWNR_BMNO")).masked()).isTrue();
	}
}
