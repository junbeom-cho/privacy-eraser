package kr.co.promptech.privacy_protector.project.application;

import kr.co.promptech.privacy_protector.project.domain.DbConnection;

public record CreateProjectCommand(String name, DbConnection rawConnection, DbConnection editConnection) {
}
