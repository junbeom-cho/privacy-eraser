package kr.co.promptech.privacy_eraser.review.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.review.domain.ColumnDecision;
import kr.co.promptech.privacy_eraser.review.domain.ColumnDecisionSheet.SheetReadResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사람이 엑셀에서 채워 올리는 파일입니다. 틀린 줄을 조용히 넘기면 무엇이 빠졌는지 알 수 없어,
 * 읽지 못한 줄은 사유와 함께 돌려줘야 합니다.
 */
class ExcelColumnDecisionSheetTest {

	private final ExcelColumnDecisionSheet sheet = new ExcelColumnDecisionSheet();

	private static final MaskingPolicy 뒤_4자리 = MaskingPolicy.partial(MaskingDirection.FROM_END, 4);

	/** 엑셀에서 사람이 채운 파일을 흉내 냅니다. */
	private static byte[] sheetOf(String... rows) {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet s = workbook.createSheet("컬럼 정의서");
			Row header = s.createRow(0);
			String[] titles = { "테이블명", "컬럼명", "마스킹", "방식", "방향", "자릿수", "이유" };
			for (int i = 0; i < titles.length; i++) {
				header.createCell(i).setCellValue(titles[i]);
			}
			for (int r = 0; r < rows.length; r++) {
				String[] cells = rows[r].split("\\|", -1);
				Row row = s.createRow(r + 1);
				for (int c = 0; c < cells.length; c++) {
					row.createCell(c).setCellValue(cells[c]);
				}
			}
			workbook.write(out);
			return out.toByteArray();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void 양식은_머리글만_있고_비어_있다() {
		// 이미 정해둔 컬럼 정의서가 따로 있으니, 현재 판정을 채워 내려주지 않습니다.
		SheetReadResult result = sheet.read(sheet.write());

		assertThat(result.decisions()).isEmpty();
		assertThat(result.errors()).isEmpty();
	}

	@Test
	void 양식의_머리글은_정해진_일곱_칸이다() throws Exception {
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(sheet.write()))) {
			Row header = workbook.getSheetAt(0).getRow(0);
			assertThat(header.getCell(0).getStringCellValue()).isEqualTo("테이블명");
			assertThat(header.getCell(1).getStringCellValue()).isEqualTo("컬럼명");
			assertThat(header.getCell(2).getStringCellValue()).isEqualTo("마스킹");
			assertThat(header.getCell(3).getStringCellValue()).isEqualTo("방식");
			assertThat(header.getCell(4).getStringCellValue()).isEqualTo("방향");
			assertThat(header.getCell(5).getStringCellValue()).isEqualTo("자릿수");
			assertThat(header.getCell(6).getStringCellValue()).isEqualTo("이유");
			assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
		}
	}

	@Test
	void 마스킹_방식_방향은_엑셀에서_골라_넣을_수_있다() throws Exception {
		// 손으로 적는 칸이라 오타 한 글자에 그 줄이 통째로 빠집니다.
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(sheet.write()))) {
			assertThat(workbook.getSheetAt(0).getDataValidations()).hasSize(3);
		}
	}

	@Test
	void 사람이_채운_한글_방향을_읽는다() {
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|PHONE|Y||뒤에서부터|4|"));

		assertThat(result.errors()).isEmpty();
		assertThat(result.decisions()).containsExactly(new ColumnDecision("EMPLOYEES", "PHONE", true, 뒤_4자리));
	}

	@Test
	void 마스킹_아님은_정책이_없어도_된다() {
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|ID|N||||"));

		assertThat(result.errors()).isEmpty();
		assertThat(result.decisions()).containsExactly(new ColumnDecision("EMPLOYEES", "ID", false, null));
	}

	@Test
	void 소문자로_적어도_대문자로_맞춘다() {
		SheetReadResult result = sheet.read(sheetOf("employees|phone|y||뒤에서부터|4|"));

		assertThat(result.decisions()).containsExactly(new ColumnDecision("EMPLOYEES", "PHONE", true, 뒤_4자리));
	}

	@Test
	void 빈_줄은_그냥_건너뛴다() {
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|PHONE|Y||뒤에서부터|4|", "|||||||"));

		assertThat(result.errors()).isEmpty();
		assertThat(result.decisions()).hasSize(1);
	}

	@Test
	void 마스킹인데_정책이_없으면_사유와_함께_알린다() {
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|PHONE|Y||||"));

		assertThat(result.decisions()).isEmpty();
		assertThat(result.errors()).hasSize(1);
		assertThat(result.errors().get(0)).contains("2행").contains("방향");
	}

	@Test
	void 자릿수가_숫자가_아니면_사유와_함께_알린다() {
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|PHONE|Y||뒤에서부터|네자리|"));

		assertThat(result.decisions()).isEmpty();
		assertThat(result.errors().get(0)).contains("2행").contains("자릿수");
	}

	@Test
	void 컬럼명이_비면_사유와_함께_알린다() {
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES||Y||뒤에서부터|4|"));

		assertThat(result.errors().get(0)).contains("2행").contains("컬럼");
	}

	@Test
	void 읽은_줄과_못_읽은_줄이_섞여도_읽은_것은_살린다() {
		SheetReadResult result = sheet.read(sheetOf(
				"EMPLOYEES|PHONE|Y||뒤에서부터|4|",
				"EMPLOYEES|BAD|Y|||4|",
				"EMPLOYEES|ID|N||||"));

		assertThat(result.decisions()).hasSize(2);
		assertThat(result.errors()).hasSize(1);
	}

	@Test
	void 엑셀_파일이_아니면_읽지_않는다() {
		SheetReadResult result = sheet.read("이건 엑셀이 아닙니다".getBytes());

		assertThat(result.decisions()).isEmpty();
		assertThat(result.errors()).hasSize(1);
		assertThat(result.errors().get(0)).contains("엑셀");
	}

	@Test
	void 자릿수가_소수로_들어와도_읽는다() {
		// 엑셀은 숫자를 double 로 저장합니다. 4 를 넣어도 4.0 으로 나옵니다.
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet s = workbook.createSheet("컬럼 정의서");
			s.createRow(0).createCell(0).setCellValue("테이블명");
			Row row = s.createRow(1);
			row.createCell(0).setCellValue("EMPLOYEES");
			row.createCell(1).setCellValue("PHONE");
			row.createCell(2).setCellValue("Y");
			row.createCell(4).setCellValue("뒤에서부터");
			row.createCell(5).setCellValue(4.0);
			workbook.write(out);

			SheetReadResult result = sheet.read(out.toByteArray());

			assertThat(result.errors()).isEmpty();
			assertThat(result.decisions().get(0).policy().length()).isEqualTo(4);
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}


	// ===== 해시 =====

	@Test
	void 방식에_해시를_적으면_해시로_읽는다() {
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|EMAIL|Y|해시|||"));

		assertThat(result.errors()).isEmpty();
		assertThat(result.decisions())
				.containsExactly(new ColumnDecision("EMPLOYEES", "EMAIL", true, MaskingPolicy.hash()));
	}

	@Test
	void 해시는_방향과_자릿수를_적지_않아도_된다() {
		// 해시에는 둘 다 의미가 없습니다.
		assertThat(sheet.read(sheetOf("EMPLOYEES|EMAIL|Y|해시|뒤에서부터|4|")).decisions())
				.containsExactly(new ColumnDecision("EMPLOYEES", "EMAIL", true, MaskingPolicy.hash()));
	}

	@Test
	void 방식을_비워두면_부분_마스킹이다() {
		// 대부분의 컬럼이 부분 마스킹이라 매번 적게 할 이유가 없습니다.
		assertThat(sheet.read(sheetOf("EMPLOYEES|PHONE|Y||뒤에서부터|4|")).decisions())
				.containsExactly(new ColumnDecision("EMPLOYEES", "PHONE", true, 뒤_4자리));
	}

	@Test
	void 모르는_방식은_사유와_함께_알린다() {
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|EMAIL|Y|암호화|||"));

		assertThat(result.decisions()).isEmpty();
		assertThat(result.errors().get(0)).contains("2행").contains("방식");
	}
}
