package kr.co.promptech.privacy_eraser.migration.domain;

/**
 * 마스킹 컬럼 하나의 이관 결과입니다.
 * <p>
 * 검수 화면의 경고는 표본 <b>1행</b> 기준이라 전체 중 몇 건이 통째로 가려졌는지는 알 수 없습니다.
 * 표본이 긴 값이면 경고가 안 뜨고, 짧은 값이면 과하게 걱정하게 됩니다. 전수를 세야 진실이 나옵니다.
 */
public record ColumnMaskingStat(String tableName, String columnName, long totalRows, long fullyMaskedRows) {

	/** 한 건도 통째로 가려지지 않았다면 알릴 것이 없습니다. */
	public boolean worthReporting() {
		return fullyMaskedRows > 0;
	}
}
