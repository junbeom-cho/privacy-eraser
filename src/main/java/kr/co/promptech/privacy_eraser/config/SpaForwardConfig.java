package kr.co.promptech.privacy_eraser.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * vue-router 는 history 모드라 {@code /projects/1} 같은 경로를 브라우저 안에서 처리합니다.
 * 그 주소로 새로고침하거나 직접 열면 요청이 서버까지 오는데, 서버에는 그런 파일이 없어 404 가 납니다.
 * 그래서 정적 파일이 없는 경로는 index.html 로 돌려보내 라우팅을 프론트에 맡깁니다.
 */
@Configuration
public class SpaForwardConfig implements WebMvcConfigurer {

	private static final Resource INDEX = new ClassPathResource("static/index.html");

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
				.addResourceLocations("classpath:/static/")
				.resourceChain(true)
				.addResolver(new PathResourceResolver() {
					@Override
					protected Resource getResource(String resourcePath, Resource location) throws IOException {
						// API 는 폴백하지 않습니다. 없는 엔드포인트는 index.html 이 아니라 404 여야 합니다.
						if (resourcePath.startsWith("api/")) {
							return null;
						}
						Resource requested = location.createRelative(resourcePath);
						if (requested.exists() && requested.isReadable()) {
							return requested;
						}
						// 확장자가 있으면 파일을 찾는 요청입니다. 없는 .js 에 index.html 을 주면
						// 브라우저가 HTML 을 스크립트로 파싱하다 엉뚱한 곳에서 터집니다.
						return resourcePath.contains(".") ? null : INDEX;
					}
				});
	}
}
