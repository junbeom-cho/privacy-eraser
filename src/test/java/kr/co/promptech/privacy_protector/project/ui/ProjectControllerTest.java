package kr.co.promptech.privacy_protector.project.ui;

import kr.co.promptech.privacy_protector.config.SecurityConfig;
import kr.co.promptech.privacy_protector.project.application.CreateProjectCommand;
import kr.co.promptech.privacy_protector.project.application.ProjectService;
import kr.co.promptech.privacy_protector.project.domain.ConnectionTestResult;
import kr.co.promptech.privacy_protector.project.domain.DbConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
@Import(SecurityConfig.class)
class ProjectControllerTest {

	private static final String CREATE_BODY = """
			{
			  "name": "고객정보 비식별화",
			  "rawConnection":  {"url":"jdbc:oracle:thin:@localhost:1521/XE","username":"app","password":"pw","schema":"RAW_SCHEMA"},
			  "editConnection": {"url":"jdbc:oracle:thin:@localhost:1521/XE","username":"app","password":"pw","schema":"EDIT_SCHEMA"}
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProjectService projectService;

	@Test
	void 프로젝트를_생성하면_201과_id를_돌려준다() throws Exception {
		given(projectService.create(any(CreateProjectCommand.class))).willReturn(7L);

		mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(7));
	}

	@Test
	void 도메인_규칙을_어기면_400과_사유를_돌려준다() throws Exception {
		given(projectService.create(any(CreateProjectCommand.class)))
				.willThrow(new IllegalArgumentException("원본과 이관 대상이 같다"));

		mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("원본과 이관 대상이 같다"));
	}

	@Test
	void 잘못된_접속정보는_서비스를_호출하지_않고_400이다() throws Exception {
		String badBody = """
				{
				  "name": "이름",
				  "rawConnection":  {"url":"oracle://localhost","username":"app","password":"pw","schema":"RAW"},
				  "editConnection": {"url":"jdbc:oracle:thin:@localhost:1521/XE","username":"app","password":"pw","schema":"EDIT"}
				}
				""";

		mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(badBody))
				.andExpect(status().isBadRequest());
		verify(projectService, never()).create(any());
	}

	@Test
	void 접속_테스트_성공을_돌려준다() throws Exception {
		given(projectService.testConnection(any(DbConnection.class))).willReturn(ConnectionTestResult.succeeded());

		String body = """
				{"url":"jdbc:oracle:thin:@localhost:1521/XE","username":"app","password":"pw","schema":"RAW_SCHEMA"}
				""";

		mockMvc.perform(post("/api/projects/connection-test").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void 접속_테스트_실패_사유를_돌려준다() throws Exception {
		given(projectService.testConnection(any(DbConnection.class)))
				.willReturn(ConnectionTestResult.failed("ORA-01017: invalid credential"));

		String body = """
				{"url":"jdbc:oracle:thin:@localhost:1521/XE","username":"app","password":"pw","schema":"RAW_SCHEMA"}
				""";

		mockMvc.perform(post("/api/projects/connection-test").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("ORA-01017: invalid credential"));
	}
}
