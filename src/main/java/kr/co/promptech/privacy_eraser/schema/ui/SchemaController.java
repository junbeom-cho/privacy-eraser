package kr.co.promptech.privacy_eraser.schema.ui;

import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.schema.application.SchemaService;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnMetadata;
import kr.co.promptech.privacy_eraser.schema.domain.TableMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/tables")
public class SchemaController {

	private final SchemaService schemaService;

	public SchemaController(SchemaService schemaService) {
		this.schemaService = schemaService;
	}

	@GetMapping
	public List<TableResponse> readTables(@PathVariable Long projectId) {
		return schemaService.readTables(projectId).stream().map(TableResponse::from).toList();
	}

	@ExceptionHandler(ProjectNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleNotFound(ProjectNotFoundException e) {
		return new ErrorResponse(e.getMessage());
	}

	/**
	 * 원본 DB 접속·조회 실패는 서버 잘못이 아니라 사용자가 넣은 접속 정보 문제입니다.
	 */
	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleReadFailure(IllegalStateException e) {
		return new ErrorResponse("원본 스키마를 읽지 못했습니다. " + e.getMessage());
	}

	public record ColumnResponse(String name, String type, boolean nullable, List<String> tokens) {

		static ColumnResponse from(ColumnMetadata column) {
			return new ColumnResponse(column.name(), column.displayType(), column.nullable(), column.tokens());
		}
	}

	public record TableResponse(String name, int columnCount, List<ColumnResponse> columns) {

		static TableResponse from(TableMetadata table) {
			return new TableResponse(table.name(), table.columns().size(),
					table.columns().stream().map(ColumnResponse::from).toList());
		}
	}

	public record ErrorResponse(String message) {
	}
}
