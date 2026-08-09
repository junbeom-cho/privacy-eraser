package kr.co.promptech.privacy_eraser.keyword.ui;

import kr.co.promptech.privacy_eraser.config.SecurityConfig;
import kr.co.promptech.privacy_eraser.keyword.application.KeywordService;
import kr.co.promptech.privacy_eraser.keyword.application.SaveKeywordCommand;
import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordNotFoundException;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KeywordRestController.class)
@Import(SecurityConfig.class)
class KeywordRestControllerTest {

	private static final String DO_BODY = """
			{"word":"phone","type":"DO","direction":"FROM_END","length":4}
			""";
	private static final String UNDO_BODY = """
			{"word":"id","type":"UNDO"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private KeywordService keywordService;

	// ===== 조회 =====

	@Test
	void 목록을_돌려준다() throws Exception {
		given(keywordService.findAll(1L)).willReturn(List.of(
				new Keyword(10L, 1L, "phone", kr.co.promptech.privacy_eraser.keyword.domain.KeywordType.DO,
						new MaskingPolicy(MaskingDirection.FROM_END, 4)),
				Keyword.skipFor(1L, "id")));

		mockMvc.perform(get("/api/projects/1/keywords"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].word").value("phone"))
				.andExpect(jsonPath("$[0].direction").value("FROM_END"))
				.andExpect(jsonPath("$[0].length").value(4))
				.andExpect(jsonPath("$[1].type").value("UNDO"))
				.andExpect(jsonPath("$[1].direction").doesNotExist())
				.andExpect(jsonPath("$[1].length").doesNotExist());
	}

	@Test
	void 없는_프로젝트의_목록은_404다() throws Exception {
		given(keywordService.findAll(999L)).willThrow(new ProjectNotFoundException(999L));

		mockMvc.perform(get("/api/projects/999/keywords"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").exists());
	}

	// ===== 등록 =====

	@Test
	void Do_키워드를_등록하면_201이다() throws Exception {
		given(keywordService.create(any(SaveKeywordCommand.class))).willReturn(10L);

		mockMvc.perform(post("/api/projects/1/keywords").contentType(MediaType.APPLICATION_JSON).content(DO_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(10));
	}

	@Test
	void Undo_키워드를_등록하면_201이다() throws Exception {
		given(keywordService.create(any(SaveKeywordCommand.class))).willReturn(11L);

		mockMvc.perform(post("/api/projects/1/keywords").contentType(MediaType.APPLICATION_JSON).content(UNDO_BODY))
				.andExpect(status().isCreated());
	}

	@Test
	void 경로의_프로젝트_id를_명령에_담는다() throws Exception {
		given(keywordService.create(any(SaveKeywordCommand.class))).willReturn(10L);

		mockMvc.perform(post("/api/projects/42/keywords").contentType(MediaType.APPLICATION_JSON).content(DO_BODY))
				.andExpect(status().isCreated());
		verify(keywordService).create(org.mockito.ArgumentMatchers.argThat(c -> c.projectId().equals(42L)));
	}

	// ===== 실패 =====

	@Test
	void Do인데_마스킹_방향이_없으면_서비스를_호출하지_않고_400이다() throws Exception {
		mockMvc.perform(post("/api/projects/1/keywords").contentType(MediaType.APPLICATION_JSON).content("""
						{"word":"phone","type":"DO","length":4}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").exists());
		verify(keywordService, never()).create(any());
	}

	@Test
	void Do인데_마스킹_개수가_없으면_서비스를_호출하지_않고_400이다() throws Exception {
		mockMvc.perform(post("/api/projects/1/keywords").contentType(MediaType.APPLICATION_JSON).content("""
						{"word":"phone","type":"DO","direction":"FROM_END"}
						"""))
				.andExpect(status().isBadRequest());
		verify(keywordService, never()).create(any());
	}

	@Test
	void 마스킹_개수가_0이하면_400이다() throws Exception {
		mockMvc.perform(post("/api/projects/1/keywords").contentType(MediaType.APPLICATION_JSON).content("""
						{"word":"phone","type":"DO","direction":"FROM_END","length":0}
						"""))
				.andExpect(status().isBadRequest());
		verify(keywordService, never()).create(any());
	}

	@Test
	void 중복_키워드는_400과_사유를_돌려준다() throws Exception {
		given(keywordService.create(any(SaveKeywordCommand.class)))
				.willThrow(new IllegalArgumentException("이미 등록된 키워드입니다: phone"));

		mockMvc.perform(post("/api/projects/1/keywords").contentType(MediaType.APPLICATION_JSON).content(DO_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이미 등록된 키워드입니다: phone"));
	}

	@Test
	void 없는_프로젝트에_등록하면_404다() throws Exception {
		given(keywordService.create(any(SaveKeywordCommand.class))).willThrow(new ProjectNotFoundException(999L));

		mockMvc.perform(post("/api/projects/999/keywords").contentType(MediaType.APPLICATION_JSON).content(DO_BODY))
				.andExpect(status().isNotFound());
	}

	// ===== 수정·삭제 =====

	@Test
	void 수정하면_204다() throws Exception {
		mockMvc.perform(put("/api/projects/1/keywords/10").contentType(MediaType.APPLICATION_JSON).content(UNDO_BODY))
				.andExpect(status().isNoContent());
		verify(keywordService).update(org.mockito.ArgumentMatchers.eq(1L),
				org.mockito.ArgumentMatchers.eq(10L), any(SaveKeywordCommand.class));
	}

	@Test
	void 남의_프로젝트_키워드를_수정하면_404다() throws Exception {
		willThrow(new KeywordNotFoundException(10L)).given(keywordService)
				.update(anyLong(), anyLong(), any(SaveKeywordCommand.class));

		mockMvc.perform(put("/api/projects/2/keywords/10").contentType(MediaType.APPLICATION_JSON).content(UNDO_BODY))
				.andExpect(status().isNotFound());
	}

	@Test
	void 삭제하면_204다() throws Exception {
		mockMvc.perform(delete("/api/projects/1/keywords/10"))
				.andExpect(status().isNoContent());
		verify(keywordService).delete(1L, 10L);
	}

	@Test
	void 남의_프로젝트_키워드를_삭제하면_404다() throws Exception {
		willThrow(new KeywordNotFoundException(10L)).given(keywordService).delete(anyLong(), anyLong());

		mockMvc.perform(delete("/api/projects/2/keywords/10"))
				.andExpect(status().isNotFound());
	}
}
