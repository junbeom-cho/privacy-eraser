package kr.co.promptech.privacy_eraser.keyword.domain;

import java.util.List;
import java.util.Optional;

public interface KeywordRepository {

	Long save(Keyword keyword);

	void update(Keyword keyword);

	void deleteById(Long id);

	List<Keyword> findAllByProjectId(Long projectId);

	Optional<Keyword> findById(Long id);

	boolean existsByProjectIdAndWord(Long projectId, String word);
}
