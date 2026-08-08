package kr.co.promptech.privacy_eraser.schema.domain;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;

import java.util.List;

/**
 * 원본 스키마의 메타데이터를 읽는 포트입니다. 구현은 infrastructure에 둡니다.
 * 조회 범위는 접속 정보에 담긴 스키마 하나로 한정합니다.
 */
public interface SchemaReader {

	List<TableMetadata> readTables(DbConnection connection);
}
