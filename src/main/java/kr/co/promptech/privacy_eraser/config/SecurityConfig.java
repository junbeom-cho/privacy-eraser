package kr.co.promptech.privacy_eraser.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	/**
	 * 아직 인증 요구사항이 없어 전부 허용한다.
	 * <p>
	 * ponytail: 인증이 없으니 CSRF도 함께 끈다(탈 세션이 없으므로 보호할 대상이 없다).
	 * 쿠키/세션 기반 인증을 넣는 순간 CSRF를 반드시 되살리고 permitAll을 좁힐 것.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
				.build();
	}
}
