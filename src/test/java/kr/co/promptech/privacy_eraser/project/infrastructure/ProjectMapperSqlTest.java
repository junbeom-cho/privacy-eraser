package kr.co.promptech.privacy_eraser.project.infrastructure;

import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.project.domain.Project;
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
 * DB 없이 매퍼 XML을 검증합니다. XML 파싱과 #{} 바인딩이 도메인 객체 구조와 맞는지까지 확인합니다.
 */
class ProjectMapperSqlTest {

	private static final String NAMESPACE = ProjectMapper.class.getName();
	private static final String URL = "jdbc:oracle:thin:@localhost:1521/XE";
	private static final DbConnection RAW = new DbConnection(URL, "raw_user", "raw_pw", "RAW_SCHEMA");
	private static final DbConnection EDIT = new DbConnection(URL, "edit_user", "edit_pw", "EDIT_SCHEMA");

	private static Configuration configuration;

	@BeforeAll
	static void loadMapperXml() throws Exception {
		configuration = new Configuration();
		try (InputStream in = ProjectMapperSqlTest.class.getResourceAsStream("/mapper/ProjectMapper.xml")) {
			new XMLMapperBuilder(in, configuration, "mapper/ProjectMapper.xml", configuration.getSqlFragments()).parse();
		}
	}

	private static Map<String, Object> insertParams(Project project, String editPassword) {
		Map<String, Object> params = new HashMap<>();
		params.put("id", 1L);
		params.put("project", project);
		params.put("rawPassword", "encrypted-raw");
		params.put("editPassword", editPassword);
		return params;
	}

	private static List<Object> boundValues(Map<String, Object> params) {
		BoundSql boundSql = configuration.getMappedStatement(NAMESPACE + ".insert").getBoundSql(params);
		MetaObject metaObject = configuration.newMetaObject(params);
		return boundSql.getParameterMappings().stream()
				.map(mapping -> metaObject.getValue(mapping.getProperty()))
				.toList();
	}

	@Test
	void 선언한_statement가_모두_등록된다() {
		assertThat(configuration.hasStatement(NAMESPACE + ".insert")).isTrue();
		assertThat(configuration.hasStatement(NAMESPACE + ".nextProjectId")).isTrue();
		assertThat(configuration.hasStatement(NAMESPACE + ".existsByName")).isTrue();
	}

	@Test
	void 이관_대상이_있으면_모든_바인딩이_해석된다() {
		Map<String, Object> params = insertParams(new Project(null, "고객정보 비식별화", RAW, EDIT), "encrypted-edit");

		BoundSql boundSql = configuration.getMappedStatement(NAMESPACE + ".insert").getBoundSql(params);
		MetaObject metaObject = configuration.newMetaObject(params);

		assertThat(boundSql.getParameterMappings()).hasSize(10);
		for (ParameterMapping mapping : boundSql.getParameterMappings()) {
			assertThat(metaObject.getValue(mapping.getProperty()))
					.as("바인딩 %s", mapping.getProperty())
					.isNotNull();
		}
	}

	@Test
	void 이관_대상은_필수라_null_로_들어가는_값이_없다() {
		Map<String, Object> params = insertParams(Project.create("프로젝트", RAW, EDIT), "encrypted-edit");

		List<Object> values = boundValues(params);

		// id, name, raw 4개, edit 4개 = 10
		assertThat(values).hasSize(10);
		assertThat(values).contains(1L, "프로젝트", RAW.url(), "encrypted-raw", EDIT.url(), "encrypted-edit");
		assertThat(values).doesNotContainNull();
	}

	@Test
	void insert에는_평문이_아니라_암호화된_비밀번호가_바인딩된다() {
		Map<String, Object> params = insertParams(new Project(null, "고객정보 비식별화", RAW, EDIT), "encrypted-edit");

		List<Object> values = boundValues(params);

		assertThat(values).contains("encrypted-raw", "encrypted-edit");
		assertThat(values).doesNotContain("raw_pw", "edit_pw");
	}
}
