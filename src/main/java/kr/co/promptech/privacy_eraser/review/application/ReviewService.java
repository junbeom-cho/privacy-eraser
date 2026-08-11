package kr.co.promptech.privacy_eraser.review.application;

import lombok.RequiredArgsConstructor;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordRepository;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import kr.co.promptech.privacy_eraser.review.domain.ColumnOverride;
import kr.co.promptech.privacy_eraser.review.domain.ColumnDecision;
import kr.co.promptech.privacy_eraser.review.domain.ColumnDecisionSheet;
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
import java.util.stream.Collectors;
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
	private final ColumnDecisionSheet columnDecisionSheet;

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

		saveOverride(projectId, new ColumnDecision(command.tableName(), command.columnName(),
				command.masked(), command.policy()));

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
	 * 컬럼 정의서에 적힌 조합을 사용자 지정으로 반영합니다.
	 * <p>
	 * 키워드는 이름 규칙이라 의도한 것보다 항상 더 많이 걸립니다. 이건 <b>딱 그 조합만</b> 바꿉니다.
	 * 정의서에 없는 컬럼은 손대지 않습니다.
	 * <p>
	 * 원본에 없는 테이블·컬럼은 반영하지 않고 사유를 모아 돌려줍니다. 조용히 넘기면
	 * 오타 한 글자에 그 줄이 빠진 것을 알 수 없습니다.
	 */
	/** 머리글만 있는 빈 양식입니다. 원본을 읽지 않으므로 프로젝트와 무관하게 같습니다. */
	public byte[] decisionSheet() {
		return columnDecisionSheet.write();
	}

	/**
	 * 올린 파일을 읽어 반영합니다. 파일을 못 읽은 사유와 반영 못 한 줄의 사유를 함께 돌려줍니다.
	 */
	@Transactional
	public ApplySheetResult applySheet(Long projectId, byte[] file) {
		ColumnDecisionSheet.SheetReadResult read = columnDecisionSheet.read(file);
		ApplySheetResult applied = applyDecisions(projectId, read.decisions());

		List<String> errors = new ArrayList<>(read.errors());
		errors.addAll(applied.errors());
		return new ApplySheetResult(applied.applied(), errors);
	}

	@Transactional
	public ApplySheetResult applyDecisions(Long projectId, List<ColumnDecision> decisions) {
		Project project = requireProject(projectId);
		Map<String, List<TableMetadata>> tables = schemaReader.readTables(project.getRawConnection()).stream()
				.collect(Collectors.groupingBy(table -> table.name().toUpperCase(Locale.ROOT)));

		int applied = 0;
		List<String> errors = new ArrayList<>();
		for (ColumnDecision decision : decisions) {
			if (!hasColumn(tables, decision)) {
				errors.add("%s.%s 는 원본에 없습니다.".formatted(decision.tableName(), decision.columnName()));
				continue;
			}
			saveOverride(projectId, decision);
			applied++;
		}
		return new ApplySheetResult(applied, errors);
	}

	private static boolean hasColumn(Map<String, List<TableMetadata>> tables, ColumnDecision decision) {
		return tables.getOrDefault(decision.tableName(), List.of()).stream()
				.flatMap(table -> table.columns().stream())
				.anyMatch(column -> column.name().equalsIgnoreCase(decision.columnName()));
	}

	private void saveOverride(Long projectId, ColumnDecision decision) {
		overrideRepository.findOne(projectId, decision.tableName(), decision.columnName())
				.ifPresentOrElse(existing -> {
					existing.change(decision.masked(), decision.policy());
					overrideRepository.update(existing);
				}, () -> overrideRepository.save(ColumnOverride.of(projectId, decision.tableName(),
						decision.columnName(), decision.masked(), decision.policy())));
	}

	/**
	 * @param applied 반영한 줄 수
	 * @param errors  반영하지 못한 줄의 사유
	 */
	public record ApplySheetResult(int applied, List<String> errors) {
	}

	/**
	 * 프로젝트의 사용자 지정을 모두 지웁니다. 전부 키워드 판정으로 돌아갑니다.
	 *
	 * @return 되돌린 뒤의 전체 목록. 바뀌는 줄이 많아 화면이 통째로 갈아끼웁니다.
	 */
	@Transactional
	public List<ColumnReview> clearAllOverrides(Long projectId) {
		requireProject(projectId);
		overrideRepository.deleteAllByProjectId(projectId);
		return review(projectId);
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
