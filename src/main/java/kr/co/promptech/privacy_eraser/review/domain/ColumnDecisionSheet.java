package kr.co.promptech.privacy_eraser.review.domain;

import java.util.List;

/**
 * 컬럼 정의서를 파일로 주고받습니다. 사람이 엑셀에서 열고 채워서 올리는 것이 전제입니다.
 */
public interface ColumnDecisionSheet {

	/**
	 * 빈 양식입니다. 작업자가 채워서 올리면 적힌 줄만 반영합니다.
	 * <p>
	 * 이미 정해둔 컬럼 정의서가 따로 있는 것이 전제라, 현재 판정을 채워 내려주지 않습니다.
	 */
	byte[] write();

	SheetReadResult read(byte[] file);

	/**
	 * @param decisions 읽어낸 줄
	 * @param errors    읽지 못한 줄의 사유. 조용히 넘기면 무엇이 빠졌는지 알 수 없습니다.
	 */
	record SheetReadResult(List<ColumnDecision> decisions, List<String> errors) {
	}
}
