package kr.co.promptech.privacy_eraser.project.ui;

import kr.co.promptech.privacy_eraser.config.SecurityConfig;
import kr.co.promptech.privacy_eraser.project.application.CreateProjectCommand;
import kr.co.promptech.privacy_eraser.project.application.ProjectService;
import kr.co.promptech.privacy_eraser.project.application.UpdateProjectCommand;
import kr.co.promptech.privacy_eraser.project.domain.ConnectionTestResult;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.project.domain.Project;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectRestController.class)
@Import(SecurityConfig.class)
class ProjectRestControllerTest {

	private static final String URL = "jdbc:oracle:thin:@localhost:1521/XE";
	private static final DbConnection RAW = new DbConnection(URL, "app", "비밀번호원문", "RAW_SCHEMA");
	private static final DbConnection EDIT = new DbConnection(URL, "app", "비밀번호원문", "EDIT_SCHEMA");

	private static final String CREATE_BODY = """
			{
			  "name": "고객정보 비식별화",
			  "rawConnection": {"url":"jdbc:oracle:thin:@localhost:1521/XE","username":"app","password":"pw","schema":"RAW_SCHEMA"},
			  "editConnection": {"url":"jdbc:oracle:thin:@localhost:1521/XE","username":"app","password":"pw","schema":"EDIT_SCHEMA"}
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProjectService projectService;

	// ===== 생성 =====

	@Test
	void 원본과_이관_대상을_주면_201과_id를_돌려준다() throws Exception {
		given(projectService.create(any(CreateProjectCommand.class))).willReturn(7L);

		mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(7));
	}

	@Test
	void 도메인_규칙을_어기면_400과_사유를_돌려준다() throws Exception {
		given(projectService.create(any(CreateProjectCommand.class)))
				.willThrow(new IllegalArgumentException("이미 존재하는 프로젝트명입니다."));

		mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이미 존재하는 프로젝트명입니다."));
	}

	@Test
	void 원본_접속정보가_없으면_서비스를_호출하지_않고_400이다() throws Exception {
		mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "이름"}
						"""))
				.andExpect(status().isBadRequest());
		verify(projectService, never()).create(any());
	}

	// ===== 조회 =====

	@Test
	void 목록을_돌려준다() throws Exception {
		given(projectService.findAll()).willReturn(List.of(
				new Project(1L, "첫번째", RAW, EDIT),
				new Project(2L, "두번째", RAW, EDIT)));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].name").value("첫번째"))
				.andExpect(jsonPath("$[0].rawConnection.schema").value("RAW_SCHEMA"))
				.andExpect(jsonPath("$[0].editConnection.schema").value("EDIT_SCHEMA"))
				.andExpect(jsonPath("$[1].editConnection.schema").value("EDIT_SCHEMA"));
	}

	@Test
	void 목록_응답에_비밀번호가_들어가지_않는다() throws Exception {
		given(projectService.findAll()).willReturn(List.of(new Project(1L, "이름", RAW, EDIT)));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("비밀번호원문"))))
				.andExpect(jsonPath("$[0].rawConnection.password").doesNotExist());
	}

	@Test
	void 단건을_돌려준다() throws Exception {
		given(projectService.findById(1L)).willReturn(new Project(1L, "이름", RAW, EDIT));

		mockMvc.perform(get("/api/projects/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.rawConnection.username").value("app"))
				.andExpect(jsonPath("$.rawConnection.password").doesNotExist());
	}

	@Test
	void 없는_프로젝트를_조회하면_404다() throws Exception {
		given(projectService.findById(999L)).willThrow(new ProjectNotFoundException(999L));

		mockMvc.perform(get("/api/projects/999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").exists());
	}

	// ===== 삭제 =====

	@Test
	void 삭제하면_204다() throws Exception {
		mockMvc.perform(delete("/api/projects/1"))
				.andExpect(status().isNoContent());
		verify(projectService).delete(1L);
	}

	@Test
	void 없는_프로젝트를_삭제하면_404다() throws Exception {
		willThrow(new ProjectNotFoundException(999L)).given(projectService).delete(999L);

		mockMvc.perform(delete("/api/projects/999"))
				.andExpect(status().isNotFound());
	}

	// ===== 수정 =====

	@Test
	void 수정하면_204다() throws Exception {
		mockMvc.perform(put("/api/projects/1").contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "name": "새이름",
						  "rawConnection": {"url":"jdbc:oracle:thin:@localhost:1521/XE","username":"app","password":"","schema":"RAW_SCHEMA"},
						  "editConnection": {"url":"jdbc:oracle:thin:@localhost:1521/XE","username":"app","password":"","schema":"EDIT_SCHEMA"}
						}
						"""))
				.andExpect(status().isNoContent());
		verify(projectService).update(any(UpdateProjectCommand.class));
	}

	@Test
	void 수정_시_경로의_id를_사용한다() throws Exception {
		mockMvc.perform(put("/api/projects/42").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isNoContent());
		verify(projectService).update(org.mockito.ArgumentMatchers.argThat(c -> c.id().equals(42L)));
	}

	@Test
	void 없는_프로젝트를_수정하면_404다() throws Exception {
		willThrow(new ProjectNotFoundException(999L)).given(projectService).update(any(UpdateProjectCommand.class));

		mockMvc.perform(put("/api/projects/999").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
				.andExpect(status().isNotFound());
	}

	// ===== 접속 테스트 =====

	@Test
	void 접속_테스트_성공을_돌려준다() throws Exception {
		given(projectService.testConnection(any(DbConnection.class))).willReturn(ConnectionTestResult.succeeded());

		mockMvc.perform(post("/api/projects/connection-test").contentType(MediaType.APPLICATION_JSON).content("""
						{"url":"jdbc:oracle:thin:@localhost:1521/XE","username":"app","password":"pw","schema":"RAW_SCHEMA"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void 접속_테스트_실패_사유를_돌려준다() throws Exception {
		given(projectService.testConnection(any(DbConnection.class)))
				.willReturn(ConnectionTestResult.failed("ORA-01017: invalid credential"));

		mockMvc.perform(post("/api/projects/connection-test").contentType(MediaType.APPLICATION_JSON).content("""
						{"url":"jdbc:oracle:thin:@localhost:1521/XE","username":"app","password":"pw","schema":"RAW_SCHEMA"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("ORA-01017: invalid credential"));
	}

	@Test
	void 잘못된_접속정보는_서비스를_호출하지_않고_400이다() throws Exception {
		mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "name": "이름",
						  "rawConnection": {"url":"oracle://localhost","username":"app","password":"pw","schema":"RAW"},
						  "editConnection": {"url":"oracle://localhost","username":"app","password":"pw","schema":"EDIT"}
						}
						"""))
				.andExpect(status().isBadRequest());
		verify(projectService, never()).create(any());
	}
}
