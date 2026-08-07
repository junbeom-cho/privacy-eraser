package kr.co.promptech.privacy_protector.project.infrastructure;

import kr.co.promptech.privacy_protector.project.domain.Project;
import kr.co.promptech.privacy_protector.project.domain.ProjectRepository;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisProjectRepository implements ProjectRepository {

	private final ProjectMapper projectMapper;
	private final CredentialCipher credentialCipher;

	public MyBatisProjectRepository(ProjectMapper projectMapper, CredentialCipher credentialCipher) {
		this.projectMapper = projectMapper;
		this.credentialCipher = credentialCipher;
	}

	@Override
	public Long save(Project project) {
		Long id = projectMapper.nextProjectId();
		projectMapper.insert(id, project,
				credentialCipher.encrypt(project.rawConnection().password()),
				credentialCipher.encrypt(project.editConnection().password()));
		return id;
	}

	@Override
	public boolean existsByName(String name) {
		return projectMapper.existsByName(name);
	}
}
