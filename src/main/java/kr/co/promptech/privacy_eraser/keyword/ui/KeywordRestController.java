package kr.co.promptech.privacy_eraser.keyword.ui;

import lombok.RequiredArgsConstructor;
import kr.co.promptech.privacy_eraser.keyword.application.KeywordService;
import kr.co.promptech.privacy_eraser.keyword.application.SaveKeywordCommand;
import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordNotFoundException;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordType;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/projects/{projectId}/keywords")
public class KeywordRestController {

	private final KeywordService keywordService;

	@GetMapping
	public List<KeywordResponse> findAll(@PathVariable Long projectId) {
		return keywordService.findAll(projectId).stream().map(KeywordResponse::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreatedResponse create(@PathVariable Long projectId, @RequestBody KeywordRequest request) {
		return new CreatedResponse(keywordService.create(request.toCommand(projectId)));
	}

	@PutMapping("/{keywordId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void update(@PathVariable Long projectId, @PathVariable Long keywordId,
			@RequestBody KeywordRequest request) {
		keywordService.update(projectId, keywordId, request.toCommand(projectId));
	}

	@DeleteMapping("/{keywordId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long projectId, @PathVariable Long keywordId) {
		keywordService.delete(projectId, keywordId);
	}

	public record KeywordRequest(String word, KeywordType type, MaskingDirection direction, Integer length) {

		SaveKeywordCommand toCommand(Long projectId) {
			return new SaveKeywordCommand(projectId, word, type, toPolicy());
		}

		/**
		 * UNDO 는 정책이 없습니다. DO 인데 값이 빠졌으면 도메인이 거부합니다.
		 */
		private MaskingPolicy toPolicy() {
			if (type != KeywordType.DO) {
				return null;
			}
			if (direction == null || length == null) {
				throw new IllegalArgumentException("Do 키워드에는 마스킹 방향과 개수가 필요합니다.");
			}
			return new MaskingPolicy(direction, length);
		}
	}

	public record KeywordResponse(Long id, String word, KeywordType type,
			MaskingDirection direction, Integer length) {

		static KeywordResponse from(Keyword keyword) {
			MaskingPolicy policy = keyword.getPolicy();
			return new KeywordResponse(keyword.getId(), keyword.getWord(), keyword.getType(),
					policy == null ? null : policy.direction(),
					policy == null ? null : policy.length());
		}
	}

	public record CreatedResponse(Long id) {
	}

}
