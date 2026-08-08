package kr.co.promptech.privacy_eraser.project.infrastructure;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.project.domain.Project;

import java.util.function.UnaryOperator;

/**
 * project 테이블 한 행. 도메인이 중첩 구조라 조회 결과를 평평하게 받은 뒤 조립합니다.
 */
public record ProjectRow(
		Long projectId, String name,
		String rawUrl, String rawUsername, String rawPassword, String rawSchema,
		String editUrl, String editUsername, String editPassword, String editSchema) {

	Project toDomain(UnaryOperator<String> decrypt) {
		return new Project(projectId, name,
				new DbConnection(rawUrl, rawUsername, decrypt.apply(rawPassword), rawSchema),
				// 이관 대상은 아직 안 정했을 수 있습니다.
				editUrl == null ? null
						: new DbConnection(editUrl, editUsername, decrypt.apply(editPassword), editSchema));
	}
}
