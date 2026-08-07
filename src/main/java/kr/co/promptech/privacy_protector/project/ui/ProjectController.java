package kr.co.promptech.privacy_protector.project.ui;

import kr.co.promptech.privacy_protector.project.application.CreateProjectCommand;
import kr.co.promptech.privacy_protector.project.application.ProjectService;
import kr.co.promptech.privacy_protector.project.domain.ConnectionTestResult;
import kr.co.promptech.privacy_protector.project.domain.DbConnection;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreateProjectResponse create(@RequestBody CreateProjectRequest request) {
		return new CreateProjectResponse(projectService.create(request.toCommand()));
	}

	@PostMapping("/connection-test")
	public ConnectionTestResult testConnection(@RequestBody DbConnectionRequest request) {
		return projectService.testConnection(request.toDomain());
	}

	/**
	 * 도메인 규칙 위반은 모두 여기로 모인다. 비밀번호가 섞이지 않도록 메시지는 도메인이 만든 것만 쓴다.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalidRequest(IllegalArgumentException e) {
		return new ErrorResponse(e.getMessage());
	}

	public record CreateProjectRequest(String name, DbConnectionRequest rawConnection,
			DbConnectionRequest editConnection) {

		CreateProjectCommand toCommand() {
			if (rawConnection == null || editConnection == null) {
				throw new IllegalArgumentException("raw/edit 접속 정보가 모두 필요합니다.");
			}
			return new CreateProjectCommand(name, rawConnection.toDomain(), editConnection.toDomain());
		}
	}

	public record DbConnectionRequest(String url, String username, String password, String schema) {

		DbConnection toDomain() {
			return new DbConnection(url, username, password, schema);
		}
	}

	public record CreateProjectResponse(Long id) {
	}

	public record ErrorResponse(String message) {
	}
}
