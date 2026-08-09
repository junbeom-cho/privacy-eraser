package kr.co.promptech.privacy_eraser.review.application;

import lombok.RequiredArgsConstructor;
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
import kr.co.promptech.privacy_eraser.schema.domain.SampleReader;
import kr.co.promptech.privacy_eraser.schema.domain.SchemaReader;
import kr.co.promptech.privacy_eraser.schema.domain.TableMetadata;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class ReviewService {

	private final ProjectRepository projectRepository;
	private final KeywordRepository keywordRepository;
	private final ColumnOverrideRepository overrideRepository;
	private final SchemaReader schemaReader;
	private final SampleReader sampleReader;

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
			// 표본은 테이블마다 한 행만 읽습니다. 컬럼마다 읽으면 조회가 컬럼 수만큼 늘어납니다.
			Map<String, String> sample = readSampleQuietly(project, table.name());
			for (ColumnMetadata column : table.columns()) {
				MaskingDecision override = overrides.get(key(table.name(), column.name()));
				rows.add(new ColumnReview(table.name(), column,
						judge.judgeWithOverride(column, override), sample.get(column.name())));
			}
		}
		return rows;
	}

	/**
	 * @return 바뀐 컬럼 한 줄. 화면이 전체를 다시 부르지 않고 이 줄만 갈아끼울 수 있게 돌려줍니다.
	 */
	@Transactional
	public ColumnReview override(Long projectId, SaveOverrideCommand command) {
		Project project = requireProject(projectId);
		ColumnMetadata column = requireColumn(project, command.tableName(), command.columnName());

		overrideRepository.findOne(projectId, command.tableName(), command.columnName())
				.ifPresentOrElse(existing -> {
					existing.change(command.masked(), command.policy());
					overrideRepository.update(existing);
				}, () -> overrideRepository.save(ColumnOverride.of(projectId, command.tableName(),
						command.columnName(), command.masked(), command.policy())));

		return reviewOne(project, command.tableName(), column);
	}

	/**
	 * 사용자 지정을 지웁니다. 해당 컬럼은 다시 키워드 판정을 따릅니다.
	 *
	 * @return 되돌린 뒤의 컬럼 한 줄
	 */
	@Transactional
	public ColumnReview clearOverride(Long projectId, String tableName, String columnName) {
		Project project = requireProject(projectId);
		ColumnMetadata column = requireColumn(project, tableName, columnName);

		overrideRepository.findOne(projectId, tableName, columnName)
				.ifPresent(override -> overrideRepository.deleteById(override.getId()));

		return reviewOne(project, tableName, column);
	}

	/**
	 * 컬럼 하나만 다시 판정합니다. 표본도 그 테이블 하나만 읽으므로 전체 조회보다 훨씬 쌉니다.
	 */
	private ColumnReview reviewOne(Project project, String tableName, ColumnMetadata column) {
		KeywordJudge judge = new KeywordJudge(keywordRepository.findAllByProjectId(project.getId()));
		MaskingDecision override = overrideRepository
				.findOne(project.getId(), tableName, column.name())
				.map(ColumnOverride::toDecision)
				.orElse(null);
		String sample = readSampleQuietly(project, tableName).get(column.name());
		return new ColumnReview(tableName.strip().toUpperCase(Locale.ROOT), column,
				judge.judgeWithOverride(column, override), sample);
	}

	/**
	 * 표본을 못 읽어도 검수 자체는 되어야 합니다. 권한이 없거나 테이블이 비어 있을 수 있습니다.
	 * 실패 사유를 값으로 남기지 않는 이유는, 그 자리에 개인정보가 아닌 오류 문자열이 섞이면 헷갈리기 때문입니다.
	 */
	private Map<String, String> readSampleQuietly(Project project, String tableName) {
		try {
			return sampleReader.readSampleRow(project.getRawConnection(), tableName);
		}
		catch (RuntimeException e) {
			return Map.of();
		}
	}

	private Project requireProject(Long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new ProjectNotFoundException(projectId));
	}

	/**
	 * 원본에 없는 컬럼을 지정하면 이관할 때가 되어서야 드러납니다. 저장 시점에 막습니다.
	 */
	private ColumnMetadata requireColumn(Project project, String tableName, String columnName) {
		return schemaReader.readTables(project.getRawConnection()).stream()
				.filter(table -> table.name().equalsIgnoreCase(tableName.strip()))
				.flatMap(table -> table.columns().stream())
				.filter(column -> column.name().equalsIgnoreCase(columnName.strip()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"원본에서 %s.%s 컬럼을 찾을 수 없습니다.".formatted(tableName, columnName)));
	}

	private static String key(String tableName, String columnName) {
		return (tableName + "." + columnName).toUpperCase(Locale.ROOT);
	}
}
