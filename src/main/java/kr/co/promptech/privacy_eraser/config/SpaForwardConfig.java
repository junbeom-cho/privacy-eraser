package kr.co.promptech.privacy_eraser.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
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

	private static final Logger log = LoggerFactory.getLogger(SpaForwardConfig.class);
	private static final String INDEX_PATH = "static/index.html";

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
				// static 은 프론트 빌드 산출물, public 은 손으로 관리하는 파일(오류 페이지)입니다.
				// 프론트 빌드가 static 을 통째로 비우므로 오류 페이지를 거기 두면 사라집니다.
				.addResourceLocations("classpath:/static/", "classpath:/public/")
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
						return resourcePath.contains(".") ? null : index();
					}
				});
	}

	/**
	 * 프론트를 빌드하지 않았으면 index.html 이 없습니다. 그때 없는 리소스를 돌려주면
	 * 요청마다 500 과 스택트레이스가 납니다. 원인을 알 수 없으므로 그냥 404 로 둡니다.
	 *
	 * @return 없으면 null
	 */
	private static Resource index() {
		Resource index = new ClassPathResource(INDEX_PATH);
		return index.exists() ? index : null;
	}

	/**
	 * 404 만 보면 왜 화면이 안 나오는지 알기 어려워 기동할 때 한 번 알려줍니다.
	 */
	@EventListener(ApplicationReadyEvent.class)
	void warnWhenFrontendIsNotBuilt() {
		if (!new ClassPathResource(INDEX_PATH).exists()) {
			log.warn("""
					프론트엔드 빌드 산출물이 없어 화면이 뜨지 않습니다. API 는 정상 동작합니다.
					  npm --prefix frontend run build   실행 후 백엔드를 재시작하세요.""");
		}
	}
}
