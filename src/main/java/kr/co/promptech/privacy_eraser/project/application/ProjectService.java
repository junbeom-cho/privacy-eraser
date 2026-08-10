package kr.co.promptech.privacy_eraser.project.application;

import lombok.RequiredArgsConstructor;
import kr.co.promptech.privacy_eraser.project.domain.ConnectionTestResult;
import kr.co.promptech.privacy_eraser.project.domain.ConnectionTester;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final ConnectionTester connectionTester;

	public ConnectionTestResult testConnection(DbConnection connection) {
		return connectionTester.test(connection);
	}

	@Transactional(readOnly = true)
	public List<Project> findAll() {
		return projectRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Project findById(Long id) {
		return projectRepository.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
	}

	@Transactional
	public Long create(CreateProjectCommand command) {
		Project project = Project.create(command.name(), command.rawConnection(), command.editConnection());
		if (projectRepository.existsByName(project.getName())) {
			throw new IllegalArgumentException("이미 존재하는 프로젝트명입니다.");
		}
		return projectRepository.save(project);
	}

	@Transactional
	public void update(UpdateProjectCommand command) {
		Project project = findById(command.id());

		if (!project.getName().equals(command.name().strip())
				&& projectRepository.existsByName(command.name().strip())) {
			throw new IllegalArgumentException("이미 존재하는 프로젝트명입니다.");
		}

		project.update(command.name(),
				keepPasswordIfBlank(command.rawConnection(), project.getRawConnection()),
				keepPasswordIfBlank(command.editConnection(), project.getEditConnection()));

		projectRepository.update(project);
	}

	@Transactional
	public void delete(Long id) {
		findById(id);
		projectRepository.deleteById(id);
	}

	/**
	 * 화면은 비밀번호를 돌려받지 못하므로, 비워서 보내면 바꾸지 않겠다는 뜻으로 봅니다.
	 */
	private static DbConnection keepPasswordIfBlank(DbConnection incoming, DbConnection existing) {
		if (incoming == null || !incoming.password().isBlank() || existing == null) {
			return incoming;
		}
		return new DbConnection(incoming.url(), incoming.username(), existing.password(), incoming.schema());
	}
}
