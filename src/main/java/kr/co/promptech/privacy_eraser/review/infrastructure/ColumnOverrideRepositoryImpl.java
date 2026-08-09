package kr.co.promptech.privacy_eraser.review.infrastructure;

import lombok.RequiredArgsConstructor;
import kr.co.promptech.privacy_eraser.review.domain.ColumnOverride;
import kr.co.promptech.privacy_eraser.review.domain.ColumnOverrideRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class ColumnOverrideRepositoryImpl implements ColumnOverrideRepository {

	private final ColumnOverrideMapper columnOverrideMapper;

	@Override
	public Long save(ColumnOverride override) {
		Long id = columnOverrideMapper.nextOverrideId();
		columnOverrideMapper.insert(id, override);
		return id;
	}

	@Override
	public void update(ColumnOverride override) {
		columnOverrideMapper.update(override);
	}

	@Override
	public void deleteById(Long id) {
		columnOverrideMapper.deleteById(id);
	}

	@Override
	public List<ColumnOverride> findAllByProjectId(Long projectId) {
		return columnOverrideMapper.findAllByProjectId(projectId).stream()
				.map(ColumnOverrideRow::toDomain).toList();
	}

	@Override
	public Optional<ColumnOverride> findOne(Long projectId, String tableName, String columnName) {
		// 저장은 대문자로 하므로 조회도 맞춰줍니다.
		return Optional.ofNullable(columnOverrideMapper.findOne(projectId,
						tableName.strip().toUpperCase(Locale.ROOT),
						columnName.strip().toUpperCase(Locale.ROOT)))
				.map(ColumnOverrideRow::toDomain);
	}
}
