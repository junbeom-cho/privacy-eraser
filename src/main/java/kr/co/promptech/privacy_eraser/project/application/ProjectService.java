package kr.co.promptech.privacy_eraser.project.application;

import kr.co.promptech.privacy_eraser.project.domain.ConnectionTestResult;
import kr.co.promptech.privacy_eraser.project.domain.ConnectionTester;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final ConnectionTester connectionTester;

	public ProjectService(ProjectRepository projectRepository, ConnectionTester connectionTester) {
		this.projectRepository = projectRepository;
		this.connectionTester = connectionTester;
	}

	public ConnectionTestResult testConnection(DbConnection connection) {
		return connectionTester.test(connection);
	}

	@Transactional
	public Long create(CreateProjectCommand command) {
		Project project = Project.create(command.name(), command.rawConnection());
		if (projectRepository.existsByName(project.name())) {
			throw new IllegalArgumentException("이미 존재하는 프로젝트명입니다.");
		}
		return projectRepository.save(project);
	}
}
