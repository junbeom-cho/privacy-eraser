package kr.co.promptech.privacy_eraser.migration.domain;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이관 대상 스키마는 도구가 만들지 않습니다. 만들려면 CREATE USER 권한이 필요하고,
 * 그 권한을 가진 계정을 앱에 저장하면 사고가 났을 때 피해가 DB 전체로 번집니다.
 * <p>
 * 대신 <b>무엇을 만들어야 하는지</b>를 알려줍니다. 쿼터나 SELECT 권한을 빠뜨리면
 * 이관 도중에야 실패하는데, 그때는 이미 시간을 다 쓴 뒤입니다.
 */
class EditSchemaScriptTest {

	private static final String URL = "jdbc:oracle:thin:@//localhost:1521/FREEPDB1";
	private static final DbConnection RAW = new DbConnection(URL, "vmisadm", "pw", "VMISADM");
	private static final DbConnection EDIT = new DbConnection(URL, "vmisadm_edit", "pw", "VMISADM_EDIT");

	@Test
	void 이관_대상_계정을_만드는_문장이_들어간다() {
		String script = EditSchemaScript.of(RAW, EDIT, List.of());

		assertThat(script).contains("CREATE USER \"VMISADM_EDIT\"");
	}

	@Test
	void 비밀번호는_스크립트에_넣지_않는다() {
		String script = EditSchemaScript.of(RAW, EDIT, List.of());

		// 저장된 접속 비밀번호는 어떤 응답에도 나가지 않습니다. 스크립트도 응답입니다.
		assertThat(script).doesNotContain("pw");
		assertThat(script).contains("<비밀번호>");
	}

	@Test
	void 공간_부족으로_중간에_실패하지_않도록_쿼터를_준다() {
		String script = EditSchemaScript.of(RAW, EDIT, List.of());

		assertThat(script).contains("GRANT UNLIMITED TABLESPACE TO \"VMISADM_EDIT\"");
	}

	@Test
	void 원본_테이블마다_SELECT_권한을_준다() {
		String script = EditSchemaScript.of(RAW, EDIT, List.of("T_BMRG_TWVH_M", "T_SCCD_CMMN_C"));

		assertThat(script).contains("GRANT SELECT ON \"VMISADM\".\"T_BMRG_TWVH_M\" TO \"VMISADM_EDIT\"");
		assertThat(script).contains("GRANT SELECT ON \"VMISADM\".\"T_SCCD_CMMN_C\" TO \"VMISADM_EDIT\"");
	}

	@Test
	void SELECT_권한은_원본_계정으로_줄_수_있다고_알려준다() {
		// 객체 소유자는 자기 객체 권한을 줄 수 있습니다. 이 부분에는 DBA 가 필요 없습니다.
		String script = EditSchemaScript.of(RAW, EDIT, List.of("T_BMRG_TWVH_M"));

		assertThat(script).contains("vmisadm");
		assertThat(script.indexOf("CREATE USER")).isLessThan(script.indexOf("GRANT SELECT ON"));
	}

	@Test
	void 테이블이_없으면_SELECT_권한_구문도_없다() {
		String script = EditSchemaScript.of(RAW, EDIT, List.of());

		assertThat(script).doesNotContain("GRANT SELECT ON");
	}

	@Test
	void 접속_계정과_스키마명이_다르면_직접_확인하라고_알린다() {
		DbConnection edit = new DbConnection(URL, "app_user", "pw", "VMISADM_EDIT");

		String script = EditSchemaScript.of(RAW, edit, List.of());

		assertThat(script).contains("APP_USER");
	}

	@Test
	void 이관_대상이_없으면_만들_수_없다() {
		assertThat(EditSchemaScript.of(RAW, null, List.of())).isEmpty();
	}
}
