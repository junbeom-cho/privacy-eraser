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
}
