package kr.co.promptech.privacy_eraser.project.application;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;

/**
 * 접속 비밀번호가 비어 있으면 기존 값을 유지합니다. 조회 응답에 비밀번호를 싣지 않으므로
 * 화면에서 다시 채워 넣을 방법이 없기 때문입니다.
 */
public record UpdateProjectCommand(Long id, String name, DbConnection rawConnection, DbConnection editConnection) {
}
