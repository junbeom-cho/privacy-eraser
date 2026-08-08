package kr.co.promptech.privacy_eraser.schema.ui;

import kr.co.promptech.privacy_eraser.config.SecurityConfig;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.schema.application.SchemaService;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;
import kr.co.promptech.privacy_eraser.schema.domain.TableMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SchemaController.class)
@Import(SecurityConfig.class)
class SchemaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SchemaService schemaService;

	@Test
	void 테이블과_컬럼을_돌려준다() throws Exception {
		given(schemaService.readTables(1L)).willReturn(List.of(
				new TableMetadata("EMPLOYEES", List.of(
						ColumnMetadata.number("EMPLOYEE_ID", 6, 0, false),
						ColumnMetadata.character("PHONE_NUMBER", "VARCHAR2", 20, true)))));

		mockMvc.perform(get("/api/projects/1/tables"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("EMPLOYEES"))
				.andExpect(jsonPath("$[0].columnCount").value(2))
				.andExpect(jsonPath("$[0].columns[1].name").value("PHONE_NUMBER"))
				.andExpect(jsonPath("$[0].columns[1].type").value("VARCHAR2(20)"))
				.andExpect(jsonPath("$[0].columns[1].nullable").value(true));
	}

	@Test
	void 컬럼명_토큰을_함께_돌려준다() throws Exception {
		given(schemaService.readTables(1L)).willReturn(List.of(
				new TableMetadata("T_USR_MSTR", List.of(
						ColumnMetadata.character("PHONE_NUMBER", "VARCHAR2", 20, true)))));

		mockMvc.perform(get("/api/projects/1/tables"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].columns[0].tokens").value(org.hamcrest.Matchers.contains("phone", "number")));
	}

	@Test
	void 없는_프로젝트면_404다() throws Exception {
		given(schemaService.readTables(999L)).willThrow(new ProjectNotFoundException(999L));

		mockMvc.perform(get("/api/projects/999/tables"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void 접속에_실패하면_400과_사유를_돌려준다() throws Exception {
		given(schemaService.readTables(1L))
				.willThrow(new IllegalStateException("ORA-01017: invalid credential"));

		mockMvc.perform(get("/api/projects/1/tables"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ORA-01017")));
	}
}
