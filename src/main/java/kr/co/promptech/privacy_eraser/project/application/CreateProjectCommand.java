package kr.co.promptech.privacy_eraser.project.application;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;

public record CreateProjectCommand(String name, DbConnection rawConnection) {
}
