package kr.co.promptech.privacy_eraser.review.application;

import kr.co.promptech.privacy_eraser.keyword.domain.KeywordRepository;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import kr.co.promptech.privacy_eraser.review.domain.ColumnOverride;
import kr.co.promptech.privacy_eraser.review.domain.ColumnOverrideRepository;
import kr.co.promptech.privacy_eraser.review.domain.ColumnReview;
import kr.co.promptech.privacy_eraser.review.domain.KeywordJudge;
import kr.co.promptech.privacy_eraser.review.domain.MaskingDecision;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;
import kr.co.promptech.privacy_eraser.schema.domain.SchemaReader;
import kr.co.promptech.privacy_eraser.schema.domain.TableMetadata;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReviewService {

	private final ProjectRepository projectRepository;
	private final KeywordRepository keywordRepository;
	private final ColumnOverrideRepository overrideRepository;
	private final SchemaReader schemaReader;

	public ReviewService(ProjectRepository projectRepository, KeywordRepository keywordRepository,
			ColumnOverrideRepository overrideRepository, SchemaReader schemaReader) {
		this.projectRepository = projectRepository;
		this.keywordRepository = keywordRepository;
		this.overrideRepository = overrideRepository;
		this.schemaReader = schemaReader;
	}

	/**
	 * 원본의 모든 컬럼에 판정을 붙여 돌려줍니다.
	 * <p>
	 * 스키마는 저장하지 않고 매번 읽습니다. 그래서 원본에 컬럼이 추가되면 자동으로 목록에 나타나고,
	 * 사용자가 직접 정한 값(override)은 그대로 남습니다.
	 */
	@Transactional(readOnly = true)
	public List<ColumnReview> review(Long projectId) {
		Project project = requireProject(projectId);
		KeywordJudge judge = new KeywordJudge(keywordRepository.findAllByProjectId(projectId));
		Map<String, MaskingDecision> overrides = overrideRepository.findAllByProjectId(projectId).stream()
				.collect(java.util.stream.Collectors.toMap(ColumnOverride::key, ColumnOverride::toDecision));

		List<ColumnReview> rows = new ArrayList<>();
		for (TableMetadata table : schemaReader.readTables(project.getRawConnection())) {
			for (ColumnMetadata column : table.columns()) {
				MaskingDecision override = overrides.get(key(table.name(), column.name()));
				rows.add(new ColumnReview(table.name(), column, judge.judgeWithOverride(column, override)));
			}
		}
		return rows;
	}

	@Transactional
	public void override(Long projectId, SaveOverrideCommand command) {
		Project project = requireProject(projectId);
		requireColumnExists(project, command.tableName(), command.columnName());

		overrideRepository.findOne(projectId, command.tableName(), command.columnName())
				.ifPresentOrElse(existing -> {
					existing.change(command.masked(), command.policy());
					overrideRepository.update(existing);
				}, () -> overrideRepository.save(ColumnOverride.of(projectId, command.tableName(),
						command.columnName(), command.masked(), command.policy())));
	}

	/**
	 * 사용자 지정을 지웁니다. 해당 컬럼은 다시 키워드 판정을 따릅니다.
	 */
	@Transactional
	public void clearOverride(Long projectId, String tableName, String columnName) {
		requireProject(projectId);
		overrideRepository.findOne(projectId, tableName, columnName)
				.ifPresent(override -> overrideRepository.deleteById(override.getId()));
	}

	private Project requireProject(Long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new ProjectNotFoundException(projectId));
	}

	/**
	 * 원본에 없는 컬럼을 지정하면 이관할 때가 되어서야 드러납니다. 저장 시점에 막습니다.
	 */
	private void requireColumnExists(Project project, String tableName, String columnName) {
		boolean exists = schemaReader.readTables(project.getRawConnection()).stream()
				.filter(table -> table.name().equalsIgnoreCase(tableName.strip()))
				.flatMap(table -> table.columns().stream())
				.anyMatch(column -> column.name().equalsIgnoreCase(columnName.strip()));
		if (!exists) {
			throw new IllegalArgumentException(
					"원본에서 %s.%s 컬럼을 찾을 수 없습니다.".formatted(tableName, columnName));
		}
	}

	private static String key(String tableName, String columnName) {
		return (tableName + "." + columnName).toUpperCase(Locale.ROOT);
	}
}
