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
 * <p>
 * 양식은 코드가 만들지 않고 프로젝트에 들어 있는 파일을 그대로 내려줍니다. 그래서 <b>읽는 쪽이
 * 자리를 고정하지 않습니다</b> — 머리글을 이름으로 찾습니다.
 */
class ExcelColumnDecisionSheetTest {

	private final ExcelColumnDecisionSheet sheet = new ExcelColumnDecisionSheet();

	private static final MaskingPolicy 뒤_4자리 = MaskingPolicy.partial(MaskingDirection.FROM_END, 4);

	private static final String[] HEADERS =
			{ "테이블명", "컬럼명", "마스킹", "방식", "방향", "자릿수", "고정값", "이유" };

	/** 엑셀에서 사람이 채운 파일을 흉내 냅니다. */
	private static byte[] sheetOf(String... rows) {
		return sheetOf(HEADERS, 0, rows);
	}

	/**
	 * @param headers   머리글 줄에 적을 이름들
	 * @param headerRow 머리글이 몇 번째 줄인지. 위쪽은 비워 둡니다
	 */
	private static byte[] sheetOf(String[] headers, int headerRow, String... rows) {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet s = workbook.createSheet("컬럼 정의서");
			Row header = s.createRow(headerRow);
			for (int i = 0; i < headers.length; i++) {
				header.createCell(i).setCellValue(headers[i]);
			}
			for (int r = 0; r < rows.length; r++) {
				String[] cells = rows[r].split("\\|", -1);
				Row row = s.createRow(headerRow + 1 + r);
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

	// ===== 양식 =====

	@Test
	void 양식은_프로젝트에_들어_있는_파일을_그대로_내려준다() throws Exception {
		// POI 로 그리지 않습니다. 서식·설명은 엑셀에서 직접 손보는 편이 낫습니다.
		byte[] file = sheet.write();

		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			assertThat(workbook.getNumberOfSheets()).isPositive();
		}
	}

	@Test
	void 양식에는_읽는_데_필요한_머리글이_들어_있다() {
		// 양식을 손으로 고치다 칸을 지우면 올려도 아무것도 반영되지 않습니다.
		SheetReadResult result = sheet.read(sheet.write());

		assertThat(result.errors()).isEmpty();
	}

	@Test
	void 양식은_비어_있다() {
		// 이미 정해둔 컬럼 정의서가 따로 있으니, 현재 판정을 채워 내려주지 않습니다.
		assertThat(sheet.read(sheet.write()).decisions()).isEmpty();
	}

	// ===== 머리글 찾기 =====

	@Test
	void 머리글이_첫_줄이_아니어도_읽는다() {
		// 사람이 만든 양식은 위에 제목이나 안내 줄을 두는 것이 보통입니다.
		SheetReadResult result = sheet.read(sheetOf(HEADERS, 3, "EMPLOYEES|PHONE|Y||뒤에서부터|4|"));

		assertThat(result.errors()).isEmpty();
		assertThat(result.decisions()).containsExactly(new ColumnDecision("EMPLOYEES", "PHONE", true, 뒤_4자리));
	}

	@Test
	void 열_순서를_바꿔도_읽는다() {
		// 자리로 읽으면 한 칸만 밀려도 전부 조용히 어긋납니다.
		String[] reordered = { "컬럼명", "자릿수", "방향", "테이블명", "마스킹" };
		SheetReadResult result = sheet.read(sheetOf(reordered, 0, "PHONE|4|뒤에서부터|EMPLOYEES|Y"));

		assertThat(result.errors()).isEmpty();
		assertThat(result.decisions()).containsExactly(new ColumnDecision("EMPLOYEES", "PHONE", true, 뒤_4자리));
	}

	@Test
	void 모르는_칸이_섞여_있어도_읽는다() {
		String[] withExtra = { "번호", "테이블명", "컬럼명", "마스킹", "방향", "자릿수", "담당자" };
		SheetReadResult result = sheet.read(sheetOf(withExtra, 0, "1|EMPLOYEES|PHONE|Y|뒤에서부터|4|조준범"));

		assertThat(result.errors()).isEmpty();
		assertThat(result.decisions()).containsExactly(new ColumnDecision("EMPLOYEES", "PHONE", true, 뒤_4자리));
	}

	@Test
	void 머리글을_찾지_못하면_한_줄도_읽지_않고_알린다() {
		// 전부 비대상으로 읽히면 아무 일도 없었던 것처럼 보입니다. 그게 제일 위험합니다.
		String[] missing = { "테이블명", "방식", "방향" };
		SheetReadResult result = sheet.read(sheetOf(missing, 0, "EMPLOYEES|부분 마스킹|뒤에서부터"));

		assertThat(result.decisions()).isEmpty();
		assertThat(result.errors()).hasSize(1);
		assertThat(result.errors().get(0)).contains("머리글").contains("컬럼명");
	}

	// ===== 읽기 =====

	@Test
	void 사람이_채운_한글_방향을_읽는다() {
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|PHONE|Y||뒤에서부터|4|"));

		assertThat(result.errors()).isEmpty();
		assertThat(result.decisions()).containsExactly(new ColumnDecision("EMPLOYEES", "PHONE", true, 뒤_4자리));
	}

	@Test
	void 마스킹_아님은_정책이_없어도_된다() {
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|ID|N|||||"));

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
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|PHONE|Y|||||"));

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
				"EMPLOYEES|BAD|Y|||4||",
				"EMPLOYEES|ID|N|||||"));

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
			Row header = s.createRow(0);
			for (int i = 0; i < HEADERS.length; i++) {
				header.createCell(i).setCellValue(HEADERS[i]);
			}
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

	// ===== 고정값 =====

	@Test
	void 방식에_고정값을_적으면_고정값으로_읽는다() {
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|PHONE|Y|고정값|||01011111111|"));

		assertThat(result.errors()).isEmpty();
		assertThat(result.decisions()).containsExactly(
				new ColumnDecision("EMPLOYEES", "PHONE", true, MaskingPolicy.fixed("01011111111")));
	}

	@Test
	void 고정값_방식인데_값이_비면_사유와_함께_알린다() {
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|PHONE|Y|고정값||||"));

		assertThat(result.decisions()).isEmpty();
		assertThat(result.errors().get(0)).contains("2행").contains("고정값");
	}

	@Test
	void 마스킹_칸이_없으면_적힌_줄은_모두_대상이다() {
		// 적은 줄만 반영하므로, 줄이 있다는 것 자체가 대상이라는 뜻입니다.
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet s = workbook.createSheet("컬럼 정의서");
			Row header = s.createRow(0);
			String[] titles = { "테이블명", "컬럼명", "방식", "방향", "자릿수", "고정값", "이유" };
			for (int i = 0; i < titles.length; i++) {
				header.createCell(i).setCellValue(titles[i]);
			}
			Row row = s.createRow(1);
			for (String v : new String[] { "EMPLOYEES", "PHONE", "부분 마스킹", "뒤에서부터", "4", "", "" }) {
				row.createCell(row.getPhysicalNumberOfCells()).setCellValue(v);
			}
			workbook.write(out);

			SheetReadResult result = sheet.read(out.toByteArray());

			assertThat(result.errors()).isEmpty();
			assertThat(result.decisions())
					.containsExactly(new ColumnDecision("EMPLOYEES", "PHONE", true, 뒤_4자리));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void 마스킹_칸이_있고_비어_있으면_대상으로_본다() {
		// 칸을 지우지 않고 그냥 안 채운 경우입니다. 적었으니 대상으로 봅니다.
		SheetReadResult result = sheet.read(sheetOf("EMPLOYEES|PHONE||부분 마스킹|뒤에서부터|4||"));

		assertThat(result.errors()).isEmpty();
		assertThat(result.decisions()).containsExactly(new ColumnDecision("EMPLOYEES", "PHONE", true, 뒤_4자리));
	}

	@Test
	void 읽을_때는_첫_시트만_본다() {
		// 작성 방법 시트가 뒤에 있어도 데이터로 읽히면 안 됩니다.
		assertThat(sheet.read(sheet.write()).decisions()).isEmpty();
		assertThat(sheet.read(sheet.write()).errors()).isEmpty();
	}
}
