package kr.co.promptech.privacy_eraser.keyword.domain;

import java.util.List;
import java.util.Optional;

public interface KeywordRepository {

	Long save(Keyword keyword);

	void update(Keyword keyword);

	void deleteById(Long id);

	/** 프로젝트의 키워드를 전부 지웁니다. 반환값은 지운 개수입니다. */
	int deleteAllByProjectId(Long projectId);

	List<Keyword> findAllByProjectId(Long projectId);

	Optional<Keyword> findById(Long id);

	boolean existsByProjectIdAndWord(Long projectId, String word);
}
