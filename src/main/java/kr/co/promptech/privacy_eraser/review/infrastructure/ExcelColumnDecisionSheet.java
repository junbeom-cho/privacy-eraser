package kr.co.promptech.privacy_eraser.review.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.review.domain.ColumnDecision;
import kr.co.promptech.privacy_eraser.review.domain.ColumnDecisionSheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 컬럼 정의서를 xlsx 로 주고받습니다. 사람이 엑셀에서 그대로 열고 채워서 올립니다.
 * <p>
 * 양식은 <b>코드가 만들지 않고</b> {@value #TEMPLATE} 에 둔 파일을 그대로 내려줍니다. 서식·설명·예시를
 * 엑셀에서 직접 손보는 편이 POI 로 그리는 것보다 낫기 때문입니다. 파일을 바꿔도 코드는 건드리지 않습니다.
 */
@Component
public class ExcelColumnDecisionSheet implements ColumnDecisionSheet {

	/**
	 * 클래스패스 경로입니다. {@code static/} 은 프론트 빌드 산출물이라 여기에 두면 지워집니다.
	 * <p>
	 * 파일명을 영어로 두는 이유는 <b>도구마다 한글 파일명을 다르게 다루기 때문입니다.</b> macOS 는
	 * 자모를 분리해(NFD) 저장하고 읽는 쪽마다 합쳐진 형태(NFC)로 바꾸는데, 이 변환을 안 하는 도구를
	 * 하나라도 거치면 파일을 못 찾습니다. 사용자에게 보이는 이름은 내려받기 헤더에서 따로 정합니다.
	 */
	static final String TEMPLATE = "/sheet/column-decision.xlsx";

	private static final String TABLE = "테이블명";
	private static final String COLUMN = "컬럼명";
	private static final String MASKED = "마스킹";
	private static final String TYPE = "방식";
	private static final String DIRECTION = "방향";
	private static final String LENGTH = "자릿수";
	private static final String FIXED_VALUE = "고정값";

	/** 이 셋이 없으면 파일 전체를 읽을 수 없습니다. 나머지는 방식에 따라 비어 있어도 됩니다. */
	private static final List<String> REQUIRED = List.of(TABLE, COLUMN, MASKED);

	/** 머리글이 표 맨 위에 없을 수 있습니다. 제목·안내 줄을 위에 두는 것이 보통입니다. */
	private static final int HEADER_SEARCH_ROWS = 20;

	/** 화면에 보이는 말 그대로 받습니다. 영문 상수명을 외우게 할 이유가 없습니다. */
	private static final String FROM_START = "앞에서부터";
	private static final String FROM_END = "뒤에서부터";
	private static final String PARTIAL = "부분 마스킹";
	private static final String HASH = "해시";
	private static final String FIXED = "고정값";

	/**
	 * 프로젝트에 들어 있는 양식 파일을 그대로 내려줍니다.
	 * <p>
	 * 작업자가 채워서 올리면 <b>적힌 줄만</b> 반영합니다. 현재 판정을 채워 내려주지 않는 이유는,
	 * 이미 정해둔 컬럼 정의서가 따로 있기 때문입니다. 그쪽 목록을 이 양식에 옮겨 적는 것이 실제 작업 흐름입니다.
	 */
	@Override
	public byte[] write() {
		try (InputStream in = ExcelColumnDecisionSheet.class.getResourceAsStream(TEMPLATE)) {
			if (in == null) {
				throw new IllegalStateException("양식 파일이 없습니다. " + TEMPLATE);
			}
			return in.readAllBytes();
		}
		catch (IOException e) {
			throw new IllegalStateException("양식 파일을 읽지 못했습니다.");
		}
	}

	/**
	 * 읽지 못한 줄은 건너뛰되 사유를 모읍니다. 조용히 넘기면 무엇이 빠졌는지 알 수 없습니다.
	 */
	@Override
	public SheetReadResult read(byte[] file) {
		List<ColumnDecision> decisions = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			Sheet sheet = workbook.getSheetAt(0);
			Header header = findHeader(sheet);
			if (header == null) {
				return new SheetReadResult(List.of(),
						List.of("머리글을 찾지 못했습니다. %s 칸이 있어야 합니다.".formatted(String.join("·", REQUIRED))));
			}
			for (int i = header.row() + 1; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null || header.isBlank(row)) {
					continue;
				}
				try {
					decisions.add(toDecision(header, row));
				}
				catch (IllegalArgumentException e) {
					errors.add("%d행: %s".formatted(i + 1, e.getMessage()));
				}
			}
		}
		catch (Exception e) {
			// 확장자만 바꾼 파일이나 손상된 파일입니다. 사유를 그대로 전하면 원인 파악이 어렵습니다.
			return new SheetReadResult(List.of(), List.of("엑셀(.xlsx) 파일이 아니거나 열 수 없습니다."));
		}
		return new SheetReadResult(decisions, errors);
	}

	/**
	 * 머리글 이름 → 열 번호.
	 * <p>
	 * 위치를 고정하지 않는 이유는 양식이 코드 밖에 있기 때문입니다. 열을 옮기거나 <code>비고</code> 같은
	 * 칸을 더 넣어도 읽을 수 있어야 합니다. 자리로 읽으면 한 칸만 밀려도 <b>전부 조용히 어긋납니다.</b>
	 */
	private record Header(int row, Map<String, Integer> columns) {

		String text(Row row, String name) {
			Integer index = columns.get(name);
			return index == null ? "" : ExcelColumnDecisionSheet.text(row, index);
		}

		Cell cell(Row row, String name) {
			Integer index = columns.get(name);
			return index == null ? null : row.getCell(index);
		}

		boolean isBlank(Row row) {
			return text(row, TABLE).isBlank() && text(row, COLUMN).isBlank();
		}
	}

	/** 머리글 줄을 이름으로 찾습니다. 위에 제목 줄이 있어도 됩니다. */
	private static Header findHeader(Sheet sheet) {
		int last = Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS);
		for (int i = 0; i <= last; i++) {
			Row row = sheet.getRow(i);
			if (row == null) {
				continue;
			}
			Map<String, Integer> columns = new HashMap<>();
			for (int c = 0; c < row.getLastCellNum(); c++) {
				String name = text(row, c).strip();
				// 같은 이름이 여러 번 나오면 처음 것을 씁니다.
				if (!name.isBlank()) {
					columns.putIfAbsent(name, c);
				}
			}
			if (columns.keySet().containsAll(REQUIRED)) {
				return new Header(i, columns);
			}
		}
		return null;
	}

	private static ColumnDecision toDecision(Header header, Row row) {
		String table = header.text(row, TABLE);
		String column = header.text(row, COLUMN);
		if (table.isBlank()) {
			throw new IllegalArgumentException("테이블명이 비어 있습니다.");
		}
		if (column.isBlank()) {
			throw new IllegalArgumentException("컬럼명이 비어 있습니다.");
		}

		boolean masked = header.text(row, MASKED).strip().equalsIgnoreCase("Y");
		if (!masked) {
			return new ColumnDecision(table, column, false, null);
		}
		return new ColumnDecision(table, column, true, policy(header, row));
	}

	/**
	 * 방식을 비워두면 부분 마스킹으로 봅니다. 대부분의 컬럼이 그쪽이라 매번 적게 할 이유가 없습니다.
	 */
	private static MaskingPolicy policy(Header header, Row row) {
		String type = header.text(row, TYPE).strip();
		if (type.equals(HASH) || type.equalsIgnoreCase("HASH")) {
			return MaskingPolicy.hash();
		}
		if (type.equals(FIXED) || type.equalsIgnoreCase("FIXED")) {
			String value = header.text(row, FIXED_VALUE).strip();
			if (value.isBlank()) {
				throw new IllegalArgumentException("'%s' 방식에는 고정값 칸을 채워야 합니다.".formatted(FIXED));
			}
			return MaskingPolicy.fixed(value);
		}
		if (!type.isBlank() && !type.equals(PARTIAL) && !type.equalsIgnoreCase("PARTIAL")) {
			throw new IllegalArgumentException(
					"방식은 '%s', '%s', '%s' 중 하나여야 합니다.".formatted(PARTIAL, HASH, FIXED));
		}
		return MaskingPolicy.partial(direction(header.text(row, DIRECTION)), length(header, row));
	}

	private static MaskingDirection direction(String value) {
		return switch (value.strip()) {
			case FROM_START, "FROM_START" -> MaskingDirection.FROM_START;
			case FROM_END, "FROM_END" -> MaskingDirection.FROM_END;
			default -> throw new IllegalArgumentException(
					"방향은 '%s' 또는 '%s' 여야 합니다.".formatted(FROM_START, FROM_END));
		};
	}

	private static int length(Header header, Row row) {
		Cell cell = header.cell(row, LENGTH);
		// 엑셀은 숫자를 double 로 저장합니다. 4 를 넣어도 4.0 으로 나옵니다.
		if (cell != null && cell.getCellType() == CellType.NUMERIC) {
			return (int) cell.getNumericCellValue();
		}
		try {
			return Integer.parseInt(header.text(row, LENGTH).strip());
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("자릿수는 1 이상의 숫자여야 합니다.");
		}
	}

	private static String text(Row row, int index) {
		Cell cell = row.getCell(index);
		if (cell == null) {
			return "";
		}
		return switch (cell.getCellType()) {
			case STRING -> cell.getStringCellValue();
			case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
			case BOOLEAN -> cell.getBooleanCellValue() ? "Y" : "N";
			default -> "";
		};
	}
}
