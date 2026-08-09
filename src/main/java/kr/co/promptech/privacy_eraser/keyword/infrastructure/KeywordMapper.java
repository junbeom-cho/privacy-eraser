package kr.co.promptech.privacy_eraser.keyword.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KeywordMapper {

	Long nextKeywordId();

	void insert(@Param("id") Long id, @Param("keyword") Keyword keyword);

	void update(@Param("keyword") Keyword keyword);

	void deleteById(@Param("id") Long id);

	List<KeywordRow> findAllByProjectId(@Param("projectId") Long projectId);

	KeywordRow findById(@Param("id") Long id);

	boolean existsByProjectIdAndWord(@Param("projectId") Long projectId, @Param("word") String word);
}
