package kr.co.promptech.privacy_eraser.config;

import kr.co.promptech.privacy_eraser.project.application.ProjectService;
import kr.co.promptech.privacy_eraser.project.ui.ProjectController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 아직 인증 요구사항이 없어 모든 요청을 허용한다. 인증을 도입하면 이 테스트부터 바꾼다.
 */
@WebMvcTest(ProjectController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProjectService projectService;

	@Test
	void 인증_없이_접근해도_401_403이_아니다() throws Exception {
		mockMvc.perform(get("/api/projects/존재하지-않는-경로"))
				.andExpect(status().isNotFound());
	}

	@Test
	void CSRF_토큰_없이도_POST가_막히지_않는다() throws Exception {
		mockMvc.perform(post("/api/projects/connection-test")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"url\":\"jdbc:postgresql://localhost:5432/db\",\"username\":\"u\",\"password\":\"p\",\"schema\":\"s\"}"))
				.andExpect(status().isOk());
	}
}
