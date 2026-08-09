package kr.co.promptech.privacy_eraser.keyword.infrastructure;

import lombok.RequiredArgsConstructor;
import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class KeywordRepositoryImpl implements KeywordRepository {

	private final KeywordMapper keywordMapper;

	@Override
	public Long save(Keyword keyword) {
		Long id = keywordMapper.nextKeywordId();
		keywordMapper.insert(id, keyword);
		return id;
	}

	@Override
	public void update(Keyword keyword) {
		keywordMapper.update(keyword);
	}

	@Override
	public void deleteById(Long id) {
		keywordMapper.deleteById(id);
	}

	@Override
	public List<Keyword> findAllByProjectId(Long projectId) {
		return keywordMapper.findAllByProjectId(projectId).stream().map(KeywordRow::toDomain).toList();
	}

	@Override
	public Optional<Keyword> findById(Long id) {
		return Optional.ofNullable(keywordMapper.findById(id)).map(KeywordRow::toDomain);
	}

	@Override
	public boolean existsByProjectIdAndWord(Long projectId, String word) {
		return keywordMapper.existsByProjectIdAndWord(projectId, word);
	}
}
