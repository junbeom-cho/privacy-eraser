package kr.co.promptech.privacy_eraser.review.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.review.domain.ColumnDecision;
import kr.co.promptech.privacy_eraser.review.domain.ColumnDecisionSheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 컬럼 정의서를 xlsx 로 주고받습니다. 사람이 엑셀에서 그대로 열고 채워서 올립니다.
 */
@Component
public class ExcelColumnDecisionSheet implements ColumnDecisionSheet {

	private static final String SHEET_NAME = "컬럼 정의서";
	private static final String[] HEADERS =
			{ "테이블명", "컬럼명", "마스킹", "방식", "방향", "자릿수", "고정값", "이유" };

	private static final int TABLE = 0;
	private static final int COLUMN = 1;
	private static final int MASKED = 2;
	private static final int TYPE = 3;
	private static final int DIRECTION = 4;
	private static final int LENGTH = 5;
	private static final int FIXED_VALUE = 6;

	/** 손으로 채우는 칸이라 고를 수 있게 해 둡니다. 오타 한 글자에 그 줄이 통째로 빠집니다. */
	private static final String[] MASKED_CHOICES = { "Y", "N" };

	/** 화면에 보이는 말 그대로 받습니다. 영문 상수명을 외우게 할 이유가 없습니다. */
	private static final String FROM_START = "앞에서부터";
	private static final String FROM_END = "뒤에서부터";
	private static final String PARTIAL = "부분 마스킹";
	private static final String HASH = "해시";
	private static final String FIXED = "고정값";

	/**
	 * 머리글만 있는 빈 양식입니다. 작업자가 채워서 올리면 <b>적힌 줄만</b> 반영합니다.
	 * <p>
	 * 현재 판정을 채워 내려주지 않는 이유는, 이미 정해둔 컬럼 정의서가 따로 있기 때문입니다.
	 * 그쪽 목록을 이 양식에 옮겨 적는 것이 실제 작업 흐름입니다.
	 */
	@Override
	public byte[] write() {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet(SHEET_NAME);
			writeHeader(workbook, sheet);
			addChoices(sheet);

			for (int i = 0; i < HEADERS.length; i++) {
				sheet.setColumnWidth(i, 18 * 256);
			}
			sheet.createFreezePane(0, 1);
			writeGuide(workbook);
			workbook.write(out);
			return out.toByteArray();
		}
		catch (Exception e) {
			throw new IllegalStateException("양식을 만들지 못했습니다. " + e.getMessage());
		}
	}

	/**
	 * 방식마다 채워야 하는 칸이 다릅니다. 빈 칸으로 두면 왜 안 됐는지 올린 뒤에야 알게 되므로
	 * 파일 안에 적어 둡니다.
	 */
	private static void writeGuide(Workbook workbook) {
		Sheet guide = workbook.createSheet("작성 방법");
		String[][] rows = {
			{ "방식", "채우는 칸", "비우는 칸", "결과", "언제 쓰나" },
			{ PARTIAL, "방향, 자릿수", "고정값", "010-1234-****",
					"대부분의 컬럼. 원본 형태를 일부 남깁니다" },
			{ HASH, "(없음)", "방향, 자릿수, 고정값", "3f2a9c… (64자)",
					"PK·UNIQUE 컬럼. 값이 겹치지 않아야 할 때" },
			{ FIXED, "고정값", "방향, 자릿수", "01011111111",
					"한 컬럼에 형식이 섞여 있을 때 (010-1234-5678 과 01012345678)" },
			{ "", "", "", "", "" },
			{ "마스킹 = N", "(없음)", "나머지 전부", "원본 그대로", "비식별화하지 않을 컬럼" },
			{ "", "", "", "", "" },
			{ "주의", "", "", "", "" },
			{ "", "방식을 비워두면 '%s' 으로 봅니다".formatted(PARTIAL), "", "", "" },
			{ "", "테이블명·컬럼명은 원본에 있는 이름과 정확히 같아야 합니다", "", "", "" },
			{ "", "적지 않은 컬럼은 손대지 않습니다", "", "", "" },
			{ "", "고정값이 컬럼 길이보다 길면 이관이 실패합니다", "", "", "" },
		};
		Font bold = workbook.createFont();
		bold.setBold(true);
		CellStyle header = workbook.createCellStyle();
		header.setFont(bold);

		for (int r = 0; r < rows.length; r++) {
			Row row = guide.createRow(r);
			for (int c = 0; c < rows[r].length; c++) {
				Cell cell = row.createCell(c);
				cell.setCellValue(rows[r][c]);
				if (r == 0 || rows[r][0].equals("주의")) {
					cell.setCellStyle(header);
				}
			}
		}
		guide.setColumnWidth(0, 14 * 256);
		guide.setColumnWidth(1, 46 * 256);
		guide.setColumnWidth(2, 24 * 256);
		guide.setColumnWidth(3, 20 * 256);
		guide.setColumnWidth(4, 56 * 256);
	}

	/** 마스킹·방향은 정해진 값만 받으므로 엑셀에서 목록으로 고르게 합니다. */
	private static void addChoices(Sheet sheet) {
		DataValidationHelper helper = sheet.getDataValidationHelper();
		addChoice(sheet, helper, MASKED, MASKED_CHOICES);
		addChoice(sheet, helper, TYPE, new String[] { PARTIAL, HASH, FIXED });
		addChoice(sheet, helper, DIRECTION, new String[] { FROM_START, FROM_END });
	}

	private static void addChoice(Sheet sheet, DataValidationHelper helper, int column, String[] choices) {
		CellRangeAddressList range = new CellRangeAddressList(1, 1000, column, column);
		DataValidation validation = helper.createValidation(
				helper.createExplicitListConstraint(choices), range);
		validation.setShowErrorBox(true);
		sheet.addValidationData(validation);
	}

	private static void writeHeader(Workbook workbook, Sheet sheet) {
		Font bold = workbook.createFont();
		bold.setBold(true);
		CellStyle style = workbook.createCellStyle();
		style.setFont(bold);

		Row header = sheet.createRow(0);
		for (int i = 0; i < HEADERS.length; i++) {
			Cell cell = header.createCell(i);
			cell.setCellValue(HEADERS[i]);
			cell.setCellStyle(style);
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
			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null || isBlank(row)) {
					continue;
				}
				try {
					decisions.add(toDecision(row));
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

	private static ColumnDecision toDecision(Row row) {
		String table = text(row, TABLE);
		String column = text(row, COLUMN);
		if (table.isBlank()) {
			throw new IllegalArgumentException("테이블명이 비어 있습니다.");
		}
		if (column.isBlank()) {
			throw new IllegalArgumentException("컬럼명이 비어 있습니다.");
		}

		boolean masked = text(row, MASKED).equalsIgnoreCase("Y");
		if (!masked) {
			return new ColumnDecision(table, column, false, null);
		}
		return new ColumnDecision(table, column, true, policy(row));
	}

	/**
	 * 방식을 비워두면 부분 마스킹으로 봅니다. 대부분의 컬럼이 그쪽이라 매번 적게 할 이유가 없습니다.
	 */
	private static MaskingPolicy policy(Row row) {
		String type = text(row, TYPE).strip();
		if (type.equals(HASH) || type.equalsIgnoreCase("HASH")) {
			return MaskingPolicy.hash();
		}
		if (type.equals(FIXED) || type.equalsIgnoreCase("FIXED")) {
			String value = text(row, FIXED_VALUE).strip();
			if (value.isBlank()) {
				throw new IllegalArgumentException("'%s' 방식에는 고정값 칸을 채워야 합니다.".formatted(FIXED));
			}
			return MaskingPolicy.fixed(value);
		}
		if (!type.isBlank() && !type.equals(PARTIAL) && !type.equalsIgnoreCase("PARTIAL")) {
			throw new IllegalArgumentException(
					"방식은 '%s', '%s', '%s' 중 하나여야 합니다.".formatted(PARTIAL, HASH, FIXED));
		}
		return MaskingPolicy.partial(direction(text(row, DIRECTION)), length(row));
	}

	private static MaskingDirection direction(String value) {
		return switch (value.strip()) {
			case FROM_START, "FROM_START" -> MaskingDirection.FROM_START;
			case FROM_END, "FROM_END" -> MaskingDirection.FROM_END;
			default -> throw new IllegalArgumentException(
					"방향은 '%s' 또는 '%s' 여야 합니다.".formatted(FROM_START, FROM_END));
		};
	}

	private static int length(Row row) {
		Cell cell = row.getCell(LENGTH);
		// 엑셀은 숫자를 double 로 저장합니다. 4 를 넣어도 4.0 으로 나옵니다.
		if (cell != null && cell.getCellType() == CellType.NUMERIC) {
			return (int) cell.getNumericCellValue();
		}
		try {
			return Integer.parseInt(text(row, LENGTH).strip());
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("자릿수는 1 이상의 숫자여야 합니다.");
		}
	}

	private static String label(MaskingDirection direction) {
		return direction == MaskingDirection.FROM_START ? FROM_START : FROM_END;
	}

	private static boolean isBlank(Row row) {
		return text(row, TABLE).isBlank() && text(row, COLUMN).isBlank();
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
