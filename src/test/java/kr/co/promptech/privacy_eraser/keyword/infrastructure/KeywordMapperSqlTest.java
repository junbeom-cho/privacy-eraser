package kr.co.promptech.privacy_eraser.keyword.infrastructure;

import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordType;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingType;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 없이 매퍼 XML을 검증합니다. 엔티티가 클래스라 MyBatis 가 게터로 바인딩을 푸는지까지 확인합니다.
 */
class KeywordMapperSqlTest {

	private static final String NAMESPACE = KeywordMapper.class.getName();

	private static Configuration configuration;

	@BeforeAll
	static void loadMapperXml() throws Exception {
		configuration = new Configuration();
		try (InputStream in = KeywordMapperSqlTest.class.getResourceAsStream("/mapper/KeywordMapper.xml")) {
			new XMLMapperBuilder(in, configuration, "mapper/KeywordMapper.xml", configuration.getSqlFragments()).parse();
		}
	}

	private static List<Object> boundValues(String statement, Map<String, Object> params) {
		BoundSql boundSql = configuration.getMappedStatement(NAMESPACE + "." + statement).getBoundSql(params);
		MetaObject metaObject = configuration.newMetaObject(params);
		return boundSql.getParameterMappings().stream()
				.map(mapping -> metaObject.getValue(mapping.getProperty()))
				.toList();
	}

	private static Map<String, Object> params(Keyword keyword) {
		Map<String, Object> params = new HashMap<>();
		params.put("id", 1L);
		params.put("keyword", keyword);
		return params;
	}

	@Test
	void 선언한_statement가_모두_등록된다() {
		for (String statement : List.of("nextKeywordId", "insert", "update", "deleteById",
				"findAllByProjectId", "findById", "existsByProjectIdAndWord")) {
			assertThat(configuration.hasStatement(NAMESPACE + "." + statement))
					.as(statement).isTrue();
		}
	}

	@Test
	void Do_키워드는_정책까지_바인딩된다() {
		Keyword keyword = Keyword.markFor(7L, "phone", MaskingPolicy.partial(MaskingDirection.FROM_END, 4));

		assertThat(boundValues("insert", params(keyword)))
				.containsExactly(1L, 7L, "phone", KeywordType.DO, MaskingType.PARTIAL, MaskingDirection.FROM_END, 4);
	}

	@Test
	void Undo_키워드는_정책_자리가_null로_해석된다() {
		Keyword keyword = Keyword.skipFor(7L, "id");

		assertThat(boundValues("insert", params(keyword)))
				.containsExactly(1L, 7L, "id", KeywordType.UNDO, null, null, null);
	}
}
