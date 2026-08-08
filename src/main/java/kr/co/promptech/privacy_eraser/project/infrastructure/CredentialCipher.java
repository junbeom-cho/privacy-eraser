package kr.co.promptech.privacy_eraser.project.infrastructure;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 프로젝트 정보 DB에 저장하는 raw/edit 접속 비밀번호를 암복호화한다.
 * <p>
 * 이 값들은 해싱할 수 없다. 타겟 Oracle에 실제로 접속해야 하므로 복호화가 가능해야 한다.
 * Argon2id는 암호화 키를 파생하는 데 쓴다.
 */
@Component
public class CredentialCipher {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final int KEY_BYTES = 32;

	// OWASP 권장(2024) 하한: m=64MiB, t=3, p=1. 기동 시 한 번만 수행한다.
	private static final int ARGON2_MEMORY_KB = 64 * 1024;
	private static final int ARGON2_ITERATIONS = 3;
	private static final int ARGON2_PARALLELISM = 1;

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	public CredentialCipher(@Value("${privacy-eraser.credential-secret}") String secret,
			@Value("${privacy-eraser.credential-salt}") String salt) {
		this.key = new SecretKeySpec(deriveKey(secret, salt), "AES");
	}

	public String encrypt(String plainText) {
		byte[] iv = new byte[IV_BYTES];
		random.nextBytes(iv);
		byte[] cipherText = doFinal(Cipher.ENCRYPT_MODE, iv, plainText.getBytes(StandardCharsets.UTF_8));

		byte[] payload = Arrays.copyOf(iv, iv.length + cipherText.length);
		System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
		return Base64.getEncoder().encodeToString(payload);
	}

	public String decrypt(String encoded) {
		byte[] payload = Base64.getDecoder().decode(encoded);
		if (payload.length <= IV_BYTES) {
			throw new IllegalStateException("암호문 형식이 올바르지 않습니다.");
		}
		byte[] iv = Arrays.copyOf(payload, IV_BYTES);
		byte[] cipherText = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
		return new String(doFinal(Cipher.DECRYPT_MODE, iv, cipherText), StandardCharsets.UTF_8);
	}

	private byte[] doFinal(int mode, byte[] iv, byte[] input) {
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
			return cipher.doFinal(input);
		}
		catch (GeneralSecurityException e) {
			// 원문·키가 메시지에 섞이지 않도록 예외 메시지를 그대로 흘리지 않는다.
			throw new IllegalStateException("접속 비밀번호 처리에 실패했습니다.");
		}
	}

	/**
	 * Argon2id로 시크릿에서 AES 키를 파생한다. 솔트는 재기동 후에도 같은 키가 나와야 하므로 설정값을 쓴다.
	 * 시크릿이나 솔트가 바뀌면 이미 저장된 비밀번호는 복호화할 수 없다.
	 */
	private static byte[] deriveKey(String secret, String salt) {
		Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
				.withVersion(Argon2Parameters.ARGON2_VERSION_13)
				.withMemoryAsKB(ARGON2_MEMORY_KB)
				.withIterations(ARGON2_ITERATIONS)
				.withParallelism(ARGON2_PARALLELISM)
				.withSalt(salt.getBytes(StandardCharsets.UTF_8))
				.build();

		Argon2BytesGenerator generator = new Argon2BytesGenerator();
		generator.init(parameters);

		byte[] derived = new byte[KEY_BYTES];
		generator.generateBytes(secret.toCharArray(), derived);
		return derived;
	}
}
