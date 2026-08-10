package kr.co.promptech.privacy_eraser.migration.domain;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;

/**
 * 실제로 테이블을 만들고 데이터를 옮기는 포트입니다. 구현은 infrastructure 에 둡니다.
 * <p>
 * 원본과 이관 대상이 같은 인스턴스라는 전제로, 데이터를 애플리케이션으로 꺼내지 않고
 * DB 안에서 처리합니다.
 */
public interface MigrationExecutor {

	/**
	 * 이관 대상에 같은 이름의 테이블이 있으면 지웁니다.
	 * 지우는 범위를 원본에 있는 테이블로 한정해, 접속을 잘못 넣었을 때 피해를 줄입니다.
	 */
	/**
	 * 이관 대상 접속을 하나 열어 둡니다.
	 * <p>
	 * 문장마다 새로 열면 테이블 수백 개짜리 스키마에서 수천 번이 되어, 리스너가
	 * {@code ORA-12516} 으로 거부합니다. 실제로 134개 테이블에서 인덱스 단계에 죽었습니다.
	 */
	void openSession(DbConnection edit);

	/** 실패하든 성공하든 반드시 닫아야 합니다. */
	void closeSession();

	void dropIfExists(DbConnection edit, String tableName);

	/**
	 * 원본에서 읽어 마스킹한 결과로 이관 대상에 테이블을 만듭니다.
	 */
	void createAndCopy(DbConnection raw, DbConnection edit, MigrationTarget target);

	void createIndex(DbConnection edit, IndexDefinition index);

	/** FK 는 참조 대상이 모두 적재된 뒤에 걸어야 합니다. 순서는 호출부가 지킵니다. */
	void addConstraint(DbConnection edit, ConstraintDefinition constraint);

	void applyComment(DbConnection edit, CommentDefinition comment);

	void createSequence(DbConnection edit, SequenceDefinition sequence);
}
