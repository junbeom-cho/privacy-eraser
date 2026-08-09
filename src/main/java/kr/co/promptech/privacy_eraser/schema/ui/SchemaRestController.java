package kr.co.promptech.privacy_eraser.schema.ui;

import lombok.RequiredArgsConstructor;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.schema.application.SchemaService;
import kr.co.promptech.privacy_eraser.schema.domain.ColumnKey;
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
import java.util.Set;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/projects/{projectId}/tables")
public class SchemaRestController {

	private final SchemaService schemaService;

	@GetMapping
	public List<TableResponse> readTables(@PathVariable Long projectId) {
		return schemaService.readTables(projectId).stream().map(TableResponse::from).toList();
	}

	public record ColumnResponse(String name, String type, boolean nullable, List<String> tokens,
			Set<ColumnKey> keys) {

		static ColumnResponse from(ColumnMetadata column) {
			return new ColumnResponse(column.name(), column.displayType(), column.nullable(),
					column.tokens(), column.keys());
		}
	}

	public record TableResponse(String name, int columnCount, List<ColumnResponse> columns) {

		static TableResponse from(TableMetadata table) {
			return new TableResponse(table.name(), table.columns().size(),
					table.columns().stream().map(ColumnResponse::from).toList());
		}
	}

}
