package kr.co.promptech.privacy_eraser.keyword.application;

import kr.co.promptech.privacy_eraser.keyword.domain.Keyword;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordNotFoundException;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordRepository;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordType;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingDirection;
import kr.co.promptech.privacy_eraser.keyword.domain.MaskingPolicy;
import kr.co.promptech.privacy_eraser.project.domain.DbConnection;
import kr.co.promptech.privacy_eraser.project.domain.Project;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import kr.co.promptech.privacy_eraser.project.domain.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeywordServiceTest {

	private static final MaskingPolicy 뒤_4자리 = new MaskingPolicy(MaskingDirection.FROM_END, 4);
	private static final DbConnection RAW =
			new DbConnection("jdbc:oracle:thin:@localhost:1521/XE", "hr", "pw", "HR");
	private static final DbConnection EDIT =
			new DbConnection("jdbc:oracle:thin:@localhost:1521/XE", "hr", "pw", "HR_EDIT");

	private FakeKeywordRepository keywords;
	private FakeProjectRepository projects;
	private KeywordService service;

	@BeforeEach
	void setUp() {
		keywords = new FakeKeywordRepository();
		projects = new FakeProjectRepository();
		projects.saved.add(new Project(1L, "프로젝트", RAW, null));
		service = new KeywordService(keywords, projects);
	}

	@Test
	void Do_키워드를_등록한다() {
		Long id = service.create(new SaveKeywordCommand(1L, "phone", KeywordType.DO, 뒤_4자리));

		assertThat(id).isEqualTo(1L);
		assertThat(service.findAll(1L)).singleElement()
				.satisfies(k -> assertThat(k.getWord()).isEqualTo("phone"));
	}

	@Test
	void Undo_키워드를_등록한다() {
		service.create(new SaveKeywordCommand(1L, "id", KeywordType.UNDO, null));

		assertThat(service.findAll(1L)).singleElement()
				.satisfies(k -> assertThat(k.getPolicy()).isNull());
	}

	@Test
	void 없는_프로젝트에는_등록할_수_없다() {
		assertThatThrownBy(() -> service.create(new SaveKeywordCommand(999L, "phone", KeywordType.DO, 뒤_4자리)))
				.isInstanceOf(ProjectNotFoundException.class);
	}

	@Test
	void 같은_프로젝트_안에서_키워드가_중복되면_등록할_수_없다() {
		service.create(new SaveKeywordCommand(1L, "phone", KeywordType.DO, 뒤_4자리));

		assertThatThrownBy(() -> service.create(new SaveKeywordCommand(1L, "PHONE", KeywordType.UNDO, null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 등록된");
	}

	@Test
	void 프로젝트가_다르면_같은_키워드를_쓸_수_있다() {
		projects.saved.add(new Project(2L, "다른 프로젝트", RAW, null));
		service.create(new SaveKeywordCommand(1L, "phone", KeywordType.DO, 뒤_4자리));

		service.create(new SaveKeywordCommand(2L, "phone", KeywordType.DO, 뒤_4자리));

		assertThat(service.findAll(1L)).hasSize(1);
		assertThat(service.findAll(2L)).hasSize(1);
	}

	@Test
	void 프로젝트의_키워드만_돌려준다() {
		projects.saved.add(new Project(2L, "다른 프로젝트", RAW, null));
		service.create(new SaveKeywordCommand(1L, "phone", KeywordType.DO, 뒤_4자리));
		service.create(new SaveKeywordCommand(2L, "email", KeywordType.DO, 뒤_4자리));

		assertThat(service.findAll(1L)).extracting(Keyword::getWord).containsExactly("phone");
	}

	@Test
	void 키워드를_수정한다() {
		Long id = service.create(new SaveKeywordCommand(1L, "phone", KeywordType.DO, 뒤_4자리));

		service.update(1L, id, new SaveKeywordCommand(1L, "email", KeywordType.UNDO, null));

		assertThat(service.findAll(1L)).singleElement().satisfies(k -> {
			assertThat(k.getWord()).isEqualTo("email");
			assertThat(k.getType()).isEqualTo(KeywordType.UNDO);
		});
	}

	@Test
	void 자기_이름_그대로_수정하는_것은_허용한다() {
		Long id = service.create(new SaveKeywordCommand(1L, "phone", KeywordType.DO, 뒤_4자리));

		service.update(1L, id, new SaveKeywordCommand(1L, "phone", KeywordType.UNDO, null));

		assertThat(service.findAll(1L)).singleElement()
				.satisfies(k -> assertThat(k.getType()).isEqualTo(KeywordType.UNDO));
	}

	@Test
	void 다른_키워드와_겹치게_수정할_수_없다() {
		service.create(new SaveKeywordCommand(1L, "phone", KeywordType.DO, 뒤_4자리));
		Long id = service.create(new SaveKeywordCommand(1L, "email", KeywordType.DO, 뒤_4자리));

		assertThatThrownBy(() -> service.update(1L, id, new SaveKeywordCommand(1L, "phone", KeywordType.DO, 뒤_4자리)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 등록된");
	}

	@Test
	void 다른_프로젝트의_키워드는_수정할_수_없다() {
		projects.saved.add(new Project(2L, "다른 프로젝트", RAW, null));
		Long id = service.create(new SaveKeywordCommand(1L, "phone", KeywordType.DO, 뒤_4자리));

		assertThatThrownBy(() -> service.update(2L, id, new SaveKeywordCommand(2L, "email", KeywordType.DO, 뒤_4자리)))
				.isInstanceOf(KeywordNotFoundException.class);
	}

	@Test
	void 키워드를_삭제한다() {
		Long id = service.create(new SaveKeywordCommand(1L, "phone", KeywordType.DO, 뒤_4자리));

		service.delete(1L, id);

		assertThat(service.findAll(1L)).isEmpty();
	}

	@Test
	void 다른_프로젝트의_키워드는_삭제할_수_없다() {
		projects.saved.add(new Project(2L, "다른 프로젝트", RAW, null));
		Long id = service.create(new SaveKeywordCommand(1L, "phone", KeywordType.DO, 뒤_4자리));

		assertThatThrownBy(() -> service.delete(2L, id))
				.isInstanceOf(KeywordNotFoundException.class);
	}

	private static class FakeKeywordRepository implements KeywordRepository {
		private final List<Keyword> saved = new ArrayList<>();
		private final AtomicLong sequence = new AtomicLong();

		@Override
		public Long save(Keyword keyword) {
			Long id = sequence.incrementAndGet();
			saved.add(new Keyword(id, keyword.getProjectId(), keyword.getWord(), keyword.getType(),
					keyword.getPolicy()));
			return id;
		}

		@Override
		public void update(Keyword keyword) {
			saved.removeIf(k -> k.getId().equals(keyword.getId()));
			saved.add(keyword);
		}

		@Override
		public void deleteById(Long id) {
			saved.removeIf(k -> k.getId().equals(id));
		}

		@Override
		public List<Keyword> findAllByProjectId(Long projectId) {
			return saved.stream().filter(k -> k.getProjectId().equals(projectId)).toList();
		}

		@Override
		public Optional<Keyword> findById(Long id) {
			return saved.stream().filter(k -> k.getId().equals(id)).findFirst();
		}

		@Override
		public boolean existsByProjectIdAndWord(Long projectId, String word) {
			return saved.stream()
					.anyMatch(k -> k.getProjectId().equals(projectId) && k.getWord().equals(word));
		}
	}

	private static class FakeProjectRepository implements ProjectRepository {
		private final List<Project> saved = new ArrayList<>();

		@Override
		public Optional<Project> findById(Long id) {
			return saved.stream().filter(p -> p.getId().equals(id)).findFirst();
		}

		@Override
		public Long save(Project project) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void update(Project project) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void deleteById(Long id) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<Project> findAll() {
			return List.copyOf(saved);
		}

		@Override
		public boolean existsByName(String name) {
			return false;
		}
	}
}
