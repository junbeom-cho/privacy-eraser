package kr.co.promptech.privacy_eraser.schema.domain;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;

import java.util.Map;

/**
 * 원본에서 표본 한 행을 읽는 포트입니다.
 * <p>
 * 여기서 나오는 값은 <b>진짜 개인정보</b>입니다. 화면에 보여주는 용도로만 쓰고
 * 로그·캐시·저장 어디에도 남기지 않습니다.
 */
public interface SampleReader {

	/**
	 * @return 컬럼명 → 값. 값이 NULL 이면 map 의 값도 null 입니다. 행이 없으면 빈 map 입니다.
	 */
	Map<String, String> readSampleRow(DbConnection connection, String tableName);
}
