package kr.co.promptech.privacy_eraser.config;

import kr.co.promptech.privacy_eraser.project.application.ProjectService;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.project.ui.ProjectRestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectRestController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProjectService projectService;

	@Test
	void 예상_못_한_예외는_500이고_내부_사정을_드러내지_않는다() throws Exception {
		given(projectService.findAll())
				.willThrow(new RuntimeException("jdbc:oracle:thin:@10.0.0.1 접속 실패 (user=admin, password=s3cret)"));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."))
				.andExpect(content().string(not(containsString("s3cret"))))
				.andExpect(content().string(not(containsString("10.0.0.1"))));
	}

	@Test
	void 도메인_규칙_위반은_400이고_사유를_그대로_전한다() throws Exception {
		given(projectService.findAll()).willThrow(new IllegalArgumentException("이미 존재하는 프로젝트명입니다."));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이미 존재하는 프로젝트명입니다."));
	}

	@Test
	void 없는_대상은_404다() throws Exception {
		given(projectService.findById(999L)).willThrow(new ProjectNotFoundException(999L));

		mockMvc.perform(get("/api/projects/999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void 원본_DB_조회_실패는_400이고_사유를_그대로_전한다() throws Exception {
		given(projectService.findAll()).willThrow(new IllegalStateException("ORA-01017: invalid credential"));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("ORA-01017")));
	}

	// ===== Spring 이 던지는 예외를 catch-all 이 삼키지 않아야 합니다 =====

	@Test
	void 매핑이_없는_경로는_500이_아니라_404다() throws Exception {
		mockMvc.perform(get("/api/매핑없는경로"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 경로변수_타입이_맞지_않으면_500이_아니라_400이다() throws Exception {
		mockMvc.perform(get("/api/projects/숫자아님"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 본문이_깨진_JSON이면_500이_아니라_400이다() throws Exception {
		mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("{깨진"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void 지원하지_않는_메서드는_500이_아니라_405다() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.patch("/api/projects/1"))
				.andExpect(status().isMethodNotAllowed());
	}
}
