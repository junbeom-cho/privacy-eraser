package kr.co.promptech.privacy_eraser.project.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionTestResultTest {

	@Test
	void 성공하면_스키마와_테이블_수를_알려준다() {
		ConnectionTestResult result = ConnectionTestResult.succeeded("HR", 7);

		assertThat(result.success()).isTrue();
		assertThat(result.message()).contains("HR").contains("7");
	}

	@Test
	void 테이블이_없으면_성공이지만_그_사실을_알린다() {
		// 이관 대상 스키마는 비어 있는 게 정상입니다. 원본이라면 잘못 지정한 것일 수 있습니다.
		ConnectionTestResult result = ConnectionTestResult.succeeded("EDIT_SCHEMA", 0);

		assertThat(result.success()).isTrue();
		assertThat(result.message()).contains("0");
	}

	@Test
	void 스키마를_찾지_못하면_실패다() {
		ConnectionTestResult result = ConnectionTestResult.schemaNotFound("HRR");

		assertThat(result.success()).isFalse();
		assertThat(result.message()).contains("HRR");
	}

	@Test
	void 실패_사유를_그대로_전달한다() {
		assertThat(ConnectionTestResult.failed("ORA-01017").message()).isEqualTo("ORA-01017");
	}
}
