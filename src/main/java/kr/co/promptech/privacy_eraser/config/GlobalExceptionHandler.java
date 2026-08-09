package kr.co.promptech.privacy_eraser.config;

import lombok.extern.slf4j.Slf4j;
import kr.co.promptech.privacy_eraser.keyword.domain.KeywordNotFoundException;
import kr.co.promptech.privacy_eraser.project.domain.ProjectNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * API 예외를 한곳에서 상태코드로 옮깁니다. 컨트롤러마다 같은 처리를 반복하지 않습니다.
 * <p>
 * {@link ResponseEntityExceptionHandler} 를 상속하는 이유는, 상속하지 않으면 아래 {@code Exception}
 * 처리기가 Spring 이 던지는 정상적인 예외(없는 경로 404, 타입 불일치 400 …)까지 삼켜 전부 500 으로
 * 만들기 때문입니다.
 * <p>
 * {@code annotations = RestController.class} 로 범위를 좁힌 이유는, 그러지 않으면 컨트롤러를 거치지 않는
 * 요청(없는 정적 파일 등)까지 이 advice 가 가로채 브라우저에 JSON 을 내보내기 때문입니다.
 * 그 경우는 Spring 기본 처리에 맡겨 {@code public/error/*.html} 오류 화면이 나가게 합니다.
 */
@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	/**
	 * 도메인 규칙 위반입니다. 메시지는 도메인이 만든 것이라 사용자에게 보여도 됩니다.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalidRequest(IllegalArgumentException e) {
		return new ErrorResponse(e.getMessage());
	}

	@ExceptionHandler({ ProjectNotFoundException.class, KeywordNotFoundException.class })
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleNotFound(RuntimeException e) {
		return new ErrorResponse(e.getMessage());
	}

	/**
	 * 원본 DB 접속·조회 실패입니다. 서버 잘못이 아니라 사용자가 넣은 접속 정보 문제라
	 * 사유를 그대로 전달해야 사용자가 고칠 수 있습니다.
	 */
	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleExternalDbFailure(IllegalStateException e) {
		return new ErrorResponse(e.getMessage());
	}

	/**
	 * 예상 못 한 예외입니다. 원인은 서버 로그에만 남기고 밖으로는 고정 문구만 내보냅니다.
	 * 스택트레이스나 SQL 에는 접속 정보·개인정보가 섞일 수 있습니다.
	 */
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ErrorResponse handleUnexpected(Exception e) {
		log.error("처리하지 못한 예외입니다.", e);
		return new ErrorResponse("요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
	}

	/**
	 * 부모가 만드는 응답은 {@link ProblemDetail} 형식이라 우리 응답과 모양이 다릅니다.
	 * 화면은 {@code message} 하나만 읽으므로 모양을 맞춰줍니다.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		String message = body instanceof ProblemDetail problem && problem.getDetail() != null
				? problem.getDetail()
				: "요청을 처리하지 못했습니다.";
		return super.handleExceptionInternal(ex, new ErrorResponse(message), headers, statusCode, request);
	}

	public record ErrorResponse(String message) {
	}
}
