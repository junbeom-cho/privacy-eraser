package kr.co.promptech.privacy_eraser.review.ui;

import kr.co.promptech.privacy_eraser.config.GlobalExceptionHandler;
import kr.co.promptech.privacy_eraser.config.SecurityConfig;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.review.application.ReviewService;
import kr.co.promptech.privacy_eraser.review.application.SaveOverrideCommand;
import kr.co.promptech.privacy_eraser.review.domain.ColumnReview;
import kr.co.promptech.privacy_eraser.review.domain.MaskingDecision;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewRestController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class ReviewRestControllerTest {

	private static final MaskingPolicy 뒤_4자리 = new MaskingPolicy(MaskingDirection.FROM_END, 4);
	private static final ColumnMetadata PHONE = ColumnMetadata.character("PHONE_NUMBER", "VARCHAR2", 20, true);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReviewService reviewService;

	private static ColumnReview masked(String sample) {
		return new ColumnReview("EMPLOYEES", PHONE, MaskingDecision.byUser(true, 뒤_4자리), sample);
	}

	@Test
	void 판정_목록을_표본과_함께_돌려준다() throws Exception {
		given(reviewService.review(1L)).willReturn(List.of(
				new ColumnReview("EMPLOYEES", PHONE,
						MaskingDecision.byDoKeyword("phone", 뒤_4자리), "01012345678")));

		mockMvc.perform(get("/api/projects/1/review"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].columnName").value("PHONE_NUMBER"))
				.andExpect(jsonPath("$[0].masked").value(true))
				.andExpect(jsonPath("$[0].matchedKeyword").value("phone"))
				.andExpect(jsonPath("$[0].sample").value("01012345678"))
				.andExpect(jsonPath("$[0].maskedSample").value("0101234****"))
				.andExpect(jsonPath("$[0].sampleFullyMasked").value(false));
	}

	@Test
	void 없는_프로젝트는_404다() throws Exception {
		given(reviewService.review(999L)).willThrow(new ProjectNotFoundException(999L));

		mockMvc.perform(get("/api/projects/999/review"))
				.andExpect(status().isNotFound());
	}

	// ===== 저장이 바뀐 줄을 돌려줘야 화면이 전체를 다시 부르지 않습니다 =====

	@Test
	void 지정을_저장하면_바뀐_줄을_돌려준다() throws Exception {
		given(reviewService.override(anyLong(), any(SaveOverrideCommand.class)))
				.willReturn(masked("01012345678"));

		mockMvc.perform(put("/api/projects/1/review/EMPLOYEES/PHONE_NUMBER")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"masked":true,"direction":"FROM_END","length":4}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.columnName").value("PHONE_NUMBER"))
				.andExpect(jsonPath("$.source").value("USER"))
				.andExpect(jsonPath("$.maskedSample").value("0101234****"));
	}

	@Test
	void 표본이_통째로_가려지면_그_사실도_함께_돌려준다() throws Exception {
		given(reviewService.override(anyLong(), any(SaveOverrideCommand.class))).willReturn(masked("123"));

		mockMvc.perform(put("/api/projects/1/review/EMPLOYEES/PHONE_NUMBER")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"masked":true,"direction":"FROM_END","length":4}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.maskedSample").value("***"))
				.andExpect(jsonPath("$.sampleFullyMasked").value(true));
	}

	@Test
	void 되돌리면_되돌린_줄을_돌려준다() throws Exception {
		given(reviewService.clearOverride(anyLong(), anyString(), anyString()))
				.willReturn(new ColumnReview("EMPLOYEES", PHONE, MaskingDecision.notMasked(), "01012345678"));

		mockMvc.perform(delete("/api/projects/1/review/EMPLOYEES/PHONE_NUMBER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.masked").value(false))
				.andExpect(jsonPath("$.source").value("NO_MATCH"))
				.andExpect(jsonPath("$.maskedSample").value("01012345678"));
	}

	@Test
	void 전체_되돌리기는_되돌린_전체_목록을_돌려준다() throws Exception {
		given(reviewService.clearAllOverrides(1L)).willReturn(List.of(
				new ColumnReview("EMPLOYEES", PHONE, MaskingDecision.notMasked(), "01012345678")));

		mockMvc.perform(delete("/api/projects/1/review"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].source").value("NO_MATCH"));
	}

	@Test
	void 마스킹하면서_정책이_빠지면_400이다() throws Exception {
		mockMvc.perform(put("/api/projects/1/review/EMPLOYEES/PHONE_NUMBER")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"masked":true}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void 원본에_없는_컬럼은_400이다() throws Exception {
		given(reviewService.override(anyLong(), any(SaveOverrideCommand.class)))
				.willThrow(new IllegalArgumentException("원본에서 EMPLOYEES.없는컬럼 컬럼을 찾을 수 없습니다."));

		mockMvc.perform(put("/api/projects/1/review/EMPLOYEES/없는컬럼")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"masked":true,"direction":"FROM_END","length":4}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("찾을 수 없습니다")));
	}
}
