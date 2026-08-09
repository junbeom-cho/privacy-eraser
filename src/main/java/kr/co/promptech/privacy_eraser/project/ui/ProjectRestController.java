package kr.co.promptech.privacy_eraser.project.ui;

import kr.co.promptech.privacy_eraser.project.application.CreateProjectCommand;
import kr.co.promptech.privacy_eraser.project.application.ProjectService;
import kr.co.promptech.privacy_eraser.project.application.UpdateProjectCommand;
import kr.co.promptech.privacy_eraser.project.domain.ConnectionTestResult;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectRestController {

	private final ProjectService projectService;

	public ProjectRestController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping
	public List<ProjectResponse> findAll() {
		return projectService.findAll().stream().map(ProjectResponse::from).toList();
	}

	@GetMapping("/{id}")
	public ProjectResponse findById(@PathVariable Long id) {
		return ProjectResponse.from(projectService.findById(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreateProjectResponse create(@RequestBody ProjectRequest request) {
		return new CreateProjectResponse(projectService.create(request.toCreateCommand()));
	}

	@PutMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void update(@PathVariable Long id, @RequestBody ProjectRequest request) {
		projectService.update(request.toUpdateCommand(id));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		projectService.delete(id);
	}

	@PostMapping("/connection-test")
	public ConnectionTestResult testConnection(@RequestBody DbConnectionRequest request) {
		return projectService.testConnection(request.toDomain());
	}



	public record ProjectRequest(String name, DbConnectionRequest rawConnection, DbConnectionRequest editConnection) {

		CreateProjectCommand toCreateCommand() {
			return new CreateProjectCommand(name, requireRaw());
		}

		UpdateProjectCommand toUpdateCommand(Long id) {
			return new UpdateProjectCommand(id, name, requireRaw(),
					editConnection == null ? null : editConnection.toDomain());
		}

		private DbConnection requireRaw() {
			if (rawConnection == null) {
				throw new IllegalArgumentException("원본(raw) 접속 정보가 필요합니다.");
			}
			return rawConnection.toDomain();
		}
	}

	public record DbConnectionRequest(String url, String username, String password, String schema) {

		DbConnection toDomain() {
			// 수정 시 비밀번호를 비워 보내면 기존 값을 유지합니다. null 도 빈 값으로 봅니다.
			return new DbConnection(url, username, password == null ? "" : password, schema);
		}
	}

	/**
	 * 응답에는 비밀번호를 절대 싣지 않습니다.
	 */
	public record ConnectionResponse(String url, String username, String schema) {

		static ConnectionResponse from(DbConnection connection) {
			return connection == null ? null
					: new ConnectionResponse(connection.url(), connection.username(), connection.schema());
		}
	}

	public record ProjectResponse(Long id, String name,
			ConnectionResponse rawConnection, ConnectionResponse editConnection) {

		static ProjectResponse from(Project project) {
			return new ProjectResponse(project.getId(), project.getName(),
					ConnectionResponse.from(project.getRawConnection()),
					ConnectionResponse.from(project.getEditConnection()));
		}
	}

	public record CreateProjectResponse(Long id) {
	}

}
