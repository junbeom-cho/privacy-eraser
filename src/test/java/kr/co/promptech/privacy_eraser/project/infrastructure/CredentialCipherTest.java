package kr.co.promptech.privacy_eraser.project.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {

	private static final String SALT = "privacy-eraser-test-salt";

	private final CredentialCipher cipher = new CredentialCipher("test-secret-key", SALT);

	@Test
	void 암호화한_값을_복호화하면_원문이다() {
		String encrypted = cipher.encrypt("oracle-password");

		assertThat(cipher.decrypt(encrypted)).isEqualTo("oracle-password");
	}

	@Test
	void 암호문에_원문이_남지_않는다() {
		assertThat(cipher.encrypt("oracle-password")).doesNotContain("oracle-password");
	}

	@Test
	void 같은_평문도_매번_다른_암호문이_된다() {
		assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
	}

	@Test
	void 다른_키로는_복호화할_수_없다() {
		String encrypted = cipher.encrypt("oracle-password");

		assertThatThrownBy(() -> new CredentialCipher("other-secret-key", SALT).decrypt(encrypted))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 빈_문자열도_왕복한다() {
		assertThat(cipher.decrypt(cipher.encrypt(""))).isEmpty();
	}

	@Test
	void 시크릿과_솔트가_같으면_다시_기동해도_같은_키를_얻는다() {
		String encrypted = cipher.encrypt("oracle-password");

		// 재기동 = 새 인스턴스. 키 파생이 결정적이지 않으면 저장된 행을 영영 못 읽는다.
		CredentialCipher restarted = new CredentialCipher("test-secret-key", SALT);

		assertThat(restarted.decrypt(encrypted)).isEqualTo("oracle-password");
	}

	@Test
	void 솔트가_다르면_복호화할_수_없다() {
		String encrypted = cipher.encrypt("oracle-password");

		assertThatThrownBy(() -> new CredentialCipher("test-secret-key", "다른-솔트").decrypt(encrypted))
				.isInstanceOf(IllegalStateException.class);
	}
}
