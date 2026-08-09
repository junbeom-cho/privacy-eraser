package kr.co.promptech.privacy_eraser.schema.application;

import lombok.RequiredArgsConstructor;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import kr.co.promptech.privacy_eraser.schema.domain.SchemaReader;
import kr.co.promptech.privacy_eraser.schema.domain.TableMetadata;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class SchemaService {

	private final ProjectRepository projectRepository;
	private final SchemaReader schemaReader;

	/**
	 * 프로젝트의 원본(raw) 스키마에서 테이블·컬럼 정보를 읽습니다.
	 */
	public List<TableMetadata> readTables(Long projectId) {
		Project project = projectRepository.findById(projectId)
				.orElseThrow(() -> new ProjectNotFoundException(projectId));
		return schemaReader.readTables(project.getRawConnection());
	}
}
