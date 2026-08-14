package kr.co.promptech.privacy_eraser.review.ui;

import lombok.RequiredArgsConstructor;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingType;
import kr.co.promptech.privacy_eraser.review.application.ReviewService;
import kr.co.promptech.privacy_eraser.review.application.SaveOverrideCommand;
import kr.co.promptech.privacy_eraser.review.domain.ColumnReview;
import kr.co.promptech.privacy_eraser.review.domain.DecisionSource;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnKey;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/projects/{projectId}/review")
public class ReviewRestController {

	/** 작업자가 받는 이름입니다. 클래스패스의 파일명과 달라도 됩니다. */
	private static final String SHEET_FILE_NAME = "비식별화_컬럼_목록.xlsx";

	private final ReviewService reviewService;

	@GetMapping
	public List<ColumnReviewResponse> review(@PathVariable Long projectId) {
		return reviewService.review(projectId).stream().map(ColumnReviewResponse::from).toList();
	}

	/**
	 * 컬럼 정의서 양식입니다. 머리글만 있고 나머지는 작업자가 채웁니다.
	 * <p>
	 * 채워서 올리면 <b>적힌 줄만</b> 반영합니다. 적지 않은 컬럼은 손대지 않습니다.
	 * <p>
	 * 내려받는 이름은 클래스패스에 둔 파일명과 <b>다릅니다.</b> 파일은 도구를 덜 타도록 영어로 두고,
	 * 사람이 받는 이름은 여기서 한글로 정합니다. 프로젝트와 무관하게 같은 파일이라 번호를 붙이지 않습니다.
	 */
	@GetMapping("/sheet")
	public ResponseEntity<byte[]> sheet() {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						// 한글 이름은 UTF-8 을 지정해야 filename* 이 함께 나갑니다. 없으면 브라우저에서 깨집니다.
						ContentDisposition.attachment()
								.filename(SHEET_FILE_NAME, StandardCharsets.UTF_8).build().toString())
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(reviewService.decisionSheet());
	}

	/**
	 * 채운 정의서를 반영합니다. 적힌 조합만 사용자 지정이 되고, 없는 줄은 손대지 않습니다.
	 */
	@PostMapping("/sheet")
	public ApplySheetResponse uploadSheet(@PathVariable Long projectId,
			@RequestParam("file") MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw new IllegalArgumentException("파일이 비어 있습니다.");
		}
		ReviewService.ApplySheetResult result = reviewService.applySheet(projectId, file.getBytes());
		return new ApplySheetResponse(result.applied(), result.errors());
	}

	/**
	 * @param applied 반영한 줄 수
	 * @param errors  반영하지 못한 줄의 사유. 조용히 넘기지 않고 그대로 보여줍니다.
	 */
	public record ApplySheetResponse(int applied, List<String> errors) {
	}

	/**
	 * 사용자 지정을 모두 지웁니다. 전부 키워드 판정으로 돌아갑니다.
	 *
	 * @return 되돌린 뒤의 전체 목록
	 */
	@DeleteMapping
	public List<ColumnReviewResponse> clearAllOverrides(@PathVariable Long projectId) {
		return reviewService.clearAllOverrides(projectId).stream().map(ColumnReviewResponse::from).toList();
	}

	/**
	 * 컬럼 하나를 사용자가 직접 정합니다. 이미 있으면 바꿉니다.
	 *
	 * @return 바뀐 줄. 화면이 전체를 다시 부르지 않고 이 줄만 갈아끼웁니다.
	 */
	@PutMapping("/{tableName}/{columnName}")
	public ColumnReviewResponse override(@PathVariable Long projectId, @PathVariable String tableName,
			@PathVariable String columnName, @RequestBody OverrideRequest request) {
		return ColumnReviewResponse.from(
				reviewService.override(projectId, request.toCommand(tableName, columnName)));
	}

	/**
	 * 사용자 지정을 지웁니다. 해당 컬럼은 다시 키워드 판정을 따릅니다.
	 *
	 * @return 되돌린 뒤의 줄
	 */
	@DeleteMapping("/{tableName}/{columnName}")
	public ColumnReviewResponse clearOverride(@PathVariable Long projectId, @PathVariable String tableName,
			@PathVariable String columnName) {
		return ColumnReviewResponse.from(reviewService.clearOverride(projectId, tableName, columnName));
	}

	public record OverrideRequest(boolean masked, MaskingType maskingType,
			MaskingDirection direction, Integer length, String fixedValue) {

		SaveOverrideCommand toCommand(String tableName, String columnName) {
			return new SaveOverrideCommand(tableName, columnName, masked, toPolicy());
		}

		private MaskingPolicy toPolicy() {
			if (!masked) {
				return null;
			}
			if (maskingType == MaskingType.HASH) {
				return MaskingPolicy.hash();
			}
			if (maskingType == MaskingType.FIXED) {
				return MaskingPolicy.fixed(fixedValue);
			}
			if (direction == null || length == null) {
				throw new IllegalArgumentException("마스킹 대상에는 방향과 개수가 필요합니다.");
			}
			return MaskingPolicy.partial(direction, length);
		}
	}

	/**
	 * {@code sample} 은 원본에서 읽은 <b>진짜 개인정보</b>입니다. 화면 표시 전용이라
	 * 로그·캐시에 남기지 않습니다.
	 */
	public record ColumnReviewResponse(String tableName, String columnName, String type, boolean nullable,
			List<String> tokens, Set<ColumnKey> keys, boolean uniqueConflict,
			boolean masked, MaskingType maskingType, MaskingDirection direction, Integer length, String fixedValue,
			DecisionSource source, String matchedKeyword, boolean policyExceedsLength,
			String sample, String maskedSample, boolean sampleFullyMasked) {

		static ColumnReviewResponse from(ColumnReview review) {
			MaskingPolicy policy = review.decision().policy();
			return new ColumnReviewResponse(
					review.tableName(),
					review.column().name(),
					review.column().displayType(),
					review.column().nullable(),
					review.column().tokens(),
					review.column().keys(),
					// 부분 마스킹하면 값이 겹쳐 이관을 시작할 수 없습니다. 여기서 먼저 알려줍니다.
					// 해시는 겹치지 않으므로 충돌이 아닙니다.
					review.column().requiresUniqueValues() && review.decision().masked()
							&& review.decision().policy().mayCollide(),
					review.decision().masked(),
					policy == null ? null : policy.type(),
					policy == null ? null : policy.direction(),
					policy == null ? null : policy.length(),
					policy == null ? null : policy.fixedValue(),
					review.decision().source(),
					review.decision().matchedKeyword(),
					review.policyExceedsColumnLength(),
					review.sample(),
					review.maskedSample(),
					review.sampleFullyMasked());
		}
	}
}
