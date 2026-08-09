package kr.co.promptech.privacy_eraser.keyword.application;

import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordNotFoundException;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordRepository;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class KeywordService {

	private final KeywordRepository keywordRepository;
	private final ProjectRepository projectRepository;

	public KeywordService(KeywordRepository keywordRepository, ProjectRepository projectRepository) {
		this.keywordRepository = keywordRepository;
		this.projectRepository = projectRepository;
	}

	@Transactional(readOnly = true)
	public List<Keyword> findAll(Long projectId) {
		requireProject(projectId);
		return keywordRepository.findAllByProjectId(projectId);
	}

	@Transactional
	public Long create(SaveKeywordCommand command) {
		requireProject(command.projectId());
		requireNotDuplicated(command.projectId(), command.word(), null);

		Keyword keyword = new Keyword(null, command.projectId(), command.word(), command.type(), command.policy());
		return keywordRepository.save(keyword);
	}

	@Transactional
	public void update(Long projectId, Long keywordId, SaveKeywordCommand command) {
		Keyword keyword = requireOwnedKeyword(projectId, keywordId);
		requireNotDuplicated(projectId, command.word(), keyword.getWord());

		keyword.update(command.word(), command.type(), command.policy());
		keywordRepository.update(keyword);
	}

	@Transactional
	public void delete(Long projectId, Long keywordId) {
		requireOwnedKeyword(projectId, keywordId);
		keywordRepository.deleteById(keywordId);
	}

	private void requireProject(Long projectId) {
		if (projectRepository.findById(projectId).isEmpty()) {
			throw new ProjectNotFoundException(projectId);
		}
	}

	/**
	 * 남의 프로젝트 키워드를 경로만 바꿔 건드리지 못하게 합니다.
	 * 존재하더라도 소유가 다르면 없는 것으로 봅니다.
	 */
	private Keyword requireOwnedKeyword(Long projectId, Long keywordId) {
		requireProject(projectId);
		return keywordRepository.findById(keywordId)
				.filter(keyword -> keyword.belongsTo(projectId))
				.orElseThrow(() -> new KeywordNotFoundException(keywordId));
	}

	/**
	 * @param currentWord 수정 중인 키워드의 기존 값. 자기 자신과의 충돌은 무시합니다.
	 */
	private void requireNotDuplicated(Long projectId, String word, String currentWord) {
		String normalized = word == null ? "" : word.strip().toLowerCase(Locale.ROOT);
		if (normalized.equals(currentWord)) {
			return;
		}
		if (keywordRepository.existsByProjectIdAndWord(projectId, normalized)) {
			throw new IllegalArgumentException("이미 등록된 키워드입니다: " + normalized);
		}
	}
}
