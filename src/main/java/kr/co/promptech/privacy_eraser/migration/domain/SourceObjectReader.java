package kr.co.promptech.privacy_eraser.migration.domain;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;

import java.util.List;

/**
 * 원본의 제약조건·인덱스·코멘트·시퀀스를 읽는 포트입니다.
 * <p>
 * {@code DBMS_METADATA} 는 {@code SELECT_CATALOG_ROLE} 이 필요해 받은 DB 에서 기대하기 어렵습니다.
 * 그래서 {@code ALL_*} 뷰를 읽어 정의를 조립합니다.
 */
public interface SourceObjectReader {

	List<ConstraintDefinition> readConstraints(DbConnection raw);

	List<IndexDefinition> readIndexes(DbConnection raw);

	List<CommentDefinition> readComments(DbConnection raw);

	List<SequenceDefinition> readSequences(DbConnection raw);

	/**
	 * 이 테이블의 마스킹 컬럼마다 전체 행수와 통째로 가려지는 행수를 셉니다.
	 * <p>
	 * 검수 화면의 경고는 표본 <b>1행</b> 기준이라, 표본이 긴 값이면 경고가 뜨지 않습니다.
	 * 실제로 몇 건이 가려졌는지는 전수를 세야 알 수 있습니다.
	 * 마스킹 컬럼이 없으면 빈 목록입니다.
	 */
	List<ColumnMaskingStat> countMasking(DbConnection raw, MigrationTarget target);
}
