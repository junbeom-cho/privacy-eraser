package kr.co.promptech.privacy_protector.project.infrastructure;

import kr.co.promptech.privacy_protector.project.domain.DbConnection;
import kr.co.promptech.privacy_protector.project.domain.Project;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
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
 * DB 없이 매퍼 XML을 검증한다. XML 파싱과 #{} 바인딩이 도메인 객체 구조와 맞는지까지 확인한다.
 */
class ProjectMapperSqlTest {

	private static final String NAMESPACE = ProjectMapper.class.getName();
	private static final String URL = "jdbc:oracle:thin:@localhost:1521/XE";

	private static Configuration configuration;

	@BeforeAll
	static void loadMapperXml() throws Exception {
		configuration = new Configuration();
		try (InputStream in = ProjectMapperSqlTest.class.getResourceAsStream("/mapper/ProjectMapper.xml")) {
			new XMLMapperBuilder(in, configuration, "mapper/ProjectMapper.xml", configuration.getSqlFragments()).parse();
		}
	}

	private static Map<String, Object> insertParams() {
		Map<String, Object> params = new HashMap<>();
		params.put("id", 1L);
		params.put("project", Project.create("고객정보 비식별화",
				new DbConnection(URL, "raw_user", "raw_pw", "RAW_SCHEMA"),
				new DbConnection(URL, "edit_user", "edit_pw", "EDIT_SCHEMA")));
		params.put("rawPassword", "encrypted-raw");
		params.put("editPassword", "encrypted-edit");
		return params;
	}

	@Test
	void 선언한_statement가_모두_등록된다() {
		assertThat(configuration.hasStatement(NAMESPACE + ".insert")).isTrue();
		assertThat(configuration.hasStatement(NAMESPACE + ".nextProjectId")).isTrue();
		assertThat(configuration.hasStatement(NAMESPACE + ".existsByName")).isTrue();
	}

	@Test
	void insert의_모든_바인딩이_도메인_객체에서_해석된다() {
		Map<String, Object> params = insertParams();
		BoundSql boundSql = configuration.getMappedStatement(NAMESPACE + ".insert").getBoundSql(params);
		MetaObject metaObject = configuration.newMetaObject(params);

		List<ParameterMapping> mappings = boundSql.getParameterMappings();
		assertThat(mappings).hasSize(10);
		for (ParameterMapping mapping : mappings) {
			assertThat(metaObject.getValue(mapping.getProperty()))
					.as("바인딩 %s", mapping.getProperty())
					.isNotNull();
		}
	}

	@Test
	void insert에는_평문이_아니라_암호화된_비밀번호가_바인딩된다() {
		Map<String, Object> params = insertParams();
		BoundSql boundSql = configuration.getMappedStatement(NAMESPACE + ".insert").getBoundSql(params);
		MetaObject metaObject = configuration.newMetaObject(params);

		List<Object> boundValues = boundSql.getParameterMappings().stream()
				.map(mapping -> metaObject.getValue(mapping.getProperty()))
				.toList();

		assertThat(boundValues).contains("encrypted-raw", "encrypted-edit");
		assertThat(boundValues).doesNotContain("raw_pw", "edit_pw");
	}
}
