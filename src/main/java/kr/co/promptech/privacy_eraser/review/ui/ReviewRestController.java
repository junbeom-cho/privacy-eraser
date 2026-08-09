package kr.co.promptech.privacy_eraser.review.ui;

import lombok.RequiredArgsConstructor;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.review.application.ReviewService;
import kr.co.promptech.privacy_eraser.review.application.SaveOverrideCommand;
import kr.co.promptech.privacy_eraser.review.domain.ColumnReview;
import kr.co.promptech.privacy_eraser.review.domain.DecisionSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/projects/{projectId}/review")
public class ReviewRestController {

	private final ReviewService reviewService;

	@GetMapping
	public List<ColumnReviewResponse> review(@PathVariable Long projectId) {
		return reviewService.review(projectId).stream().map(ColumnReviewResponse::from).toList();
	}

	/**
	 * 컬럼 하나를 사용자가 직접 정합니다. 이미 있으면 바꿉니다.
	 */
	@PutMapping("/{tableName}/{columnName}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void override(@PathVariable Long projectId, @PathVariable String tableName,
			@PathVariable String columnName, @RequestBody OverrideRequest request) {
		reviewService.override(projectId, request.toCommand(tableName, columnName));
	}

	/**
	 * 사용자 지정을 지웁니다. 해당 컬럼은 다시 키워드 판정을 따릅니다.
	 */
	@DeleteMapping("/{tableName}/{columnName}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void clearOverride(@PathVariable Long projectId, @PathVariable String tableName,
			@PathVariable String columnName) {
		reviewService.clearOverride(projectId, tableName, columnName);
	}

	public record OverrideRequest(boolean masked, MaskingDirection direction, Integer length) {

		SaveOverrideCommand toCommand(String tableName, String columnName) {
			return new SaveOverrideCommand(tableName, columnName, masked, toPolicy());
		}

		private MaskingPolicy toPolicy() {
			if (!masked) {
				return null;
			}
			if (direction == null || length == null) {
				throw new IllegalArgumentException("마스킹 대상에는 방향과 개수가 필요합니다.");
			}
			return new MaskingPolicy(direction, length);
		}
	}

	public record ColumnReviewResponse(String tableName, String columnName, String type, boolean nullable,
			List<String> tokens, boolean masked, MaskingDirection direction, Integer length,
			DecisionSource source, String matchedKeyword, boolean policyExceedsLength) {

		static ColumnReviewResponse from(ColumnReview review) {
			MaskingPolicy policy = review.decision().policy();
			return new ColumnReviewResponse(
					review.tableName(),
					review.column().name(),
					review.column().displayType(),
					review.column().nullable(),
					review.column().tokens(),
					review.decision().masked(),
					policy == null ? null : policy.direction(),
					policy == null ? null : policy.length(),
					review.decision().source(),
					review.decision().matchedKeyword(),
					review.policyExceedsColumnLength());
		}
	}
}
