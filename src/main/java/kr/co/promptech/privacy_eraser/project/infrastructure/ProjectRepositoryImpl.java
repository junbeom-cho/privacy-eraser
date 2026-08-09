package kr.co.promptech.privacy_eraser.project.infrastructure;

import lombok.RequiredArgsConstructor;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class ProjectRepositoryImpl implements ProjectRepository {

	private final ProjectMapper projectMapper;
	private final CredentialCipher credentialCipher;

	@Override
	public Long save(Project project) {
		Long id = projectMapper.nextProjectId();
		projectMapper.insert(id, project, encryptRaw(project), encryptEdit(project));
		return id;
	}

	@Override
	public void update(Project project) {
		projectMapper.update(project, encryptRaw(project), encryptEdit(project));
	}

	@Override
	public void deleteById(Long id) {
		projectMapper.deleteById(id);
	}

	@Override
	public List<Project> findAll() {
		return projectMapper.findAll().stream().map(this::toDomain).toList();
	}

	@Override
	public Optional<Project> findById(Long id) {
		return Optional.ofNullable(projectMapper.findById(id)).map(this::toDomain);
	}

	@Override
	public boolean existsByName(String name) {
		return projectMapper.existsByName(name);
	}

	private Project toDomain(ProjectRow row) {
		return row.toDomain(credentialCipher::decrypt);
	}

	private String encryptRaw(Project project) {
		return credentialCipher.encrypt(project.getRawConnection().password());
	}

	/** 이관 대상은 나중에 정할 수 있으므로 없을 수 있습니다. */
	private String encryptEdit(Project project) {
		return project.hasEditConnection() ? credentialCipher.encrypt(project.getEditConnection().password()) : null;
	}
}
