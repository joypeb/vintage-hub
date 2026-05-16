package com.joypeb.vintagehub.common.api;

import com.joypeb.vintagehub.auth.InvalidAdminCredentialsException;
import com.joypeb.vintagehub.auth.PasswordHashApiDisabledException;
import com.joypeb.vintagehub.crawl.application.CrawlRunAlreadyActiveException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestControllerAdvice
class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException exception,
			HttpServletRequest request) {
		log.atWarn()
			.addKeyValue("event", "api.error.handled")
			.addKeyValue("path", request.getRequestURI())
			.addKeyValue("status", 400)
			.addKeyValue("errorCode", ErrorCode.INVALID_REQUEST)
			.addKeyValue("reason", exception.getMessage())
			.log("api.error.handled");
		return ResponseEntity.badRequest()
			.body(ApiResponse.error(ErrorCode.INVALID_REQUEST, exception.getMessage()));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException exception,
			HttpServletRequest request) {
		log.atWarn()
			.addKeyValue("event", "api.error.handled")
			.addKeyValue("path", request.getRequestURI())
			.addKeyValue("status", 404)
			.addKeyValue("errorCode", ErrorCode.NOT_FOUND)
			.addKeyValue("reason", exception.getMessage())
			.log("api.error.handled");
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiResponse.error(ErrorCode.NOT_FOUND, exception.getMessage()));
	}

	@ExceptionHandler(CrawlRunAlreadyActiveException.class)
	ResponseEntity<ApiResponse<Void>> handleCrawlRunAlreadyActiveException(CrawlRunAlreadyActiveException exception,
			HttpServletRequest request) {
		log.atWarn()
			.addKeyValue("event", "api.error.handled")
			.addKeyValue("path", request.getRequestURI())
			.addKeyValue("status", 409)
			.addKeyValue("errorCode", ErrorCode.CONFLICT)
			.addKeyValue("reason", exception.getMessage())
			.log("api.error.handled");
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(ApiResponse.error(ErrorCode.CONFLICT, exception.getMessage()));
	}

	@ExceptionHandler(InvalidAdminCredentialsException.class)
	ResponseEntity<ApiResponse<Void>> handleInvalidAdminCredentialsException(InvalidAdminCredentialsException exception,
			HttpServletRequest request) {
		log.atWarn()
			.addKeyValue("event", "api.error.handled")
			.addKeyValue("path", request.getRequestURI())
			.addKeyValue("status", 401)
			.addKeyValue("errorCode", ErrorCode.UNAUTHORIZED)
			.addKeyValue("reason", exception.getMessage())
			.log("api.error.handled");
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(ApiResponse.error(ErrorCode.UNAUTHORIZED, exception.getMessage()));
	}

	@ExceptionHandler(PasswordHashApiDisabledException.class)
	ResponseEntity<ApiResponse<Void>> handlePasswordHashApiDisabledException(PasswordHashApiDisabledException exception,
			HttpServletRequest request) {
		log.atWarn()
			.addKeyValue("event", "api.error.handled")
			.addKeyValue("path", request.getRequestURI())
			.addKeyValue("status", 403)
			.addKeyValue("errorCode", ErrorCode.FORBIDDEN)
			.addKeyValue("reason", exception.getMessage())
			.log("api.error.handled");
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(ApiResponse.error(ErrorCode.FORBIDDEN, exception.getMessage()));
	}

	@ExceptionHandler(AsyncRequestTimeoutException.class)
	ResponseEntity<Void> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException exception,
			HttpServletRequest request) {
		log.atDebug()
			.addKeyValue("event", "api.async.timeout")
			.addKeyValue("path", request.getRequestURI())
			.addKeyValue("status", 204)
			.log("api.async.timeout");
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiResponse<Void>> handleException(Exception exception, HttpServletRequest request) {
		log.atError()
			.setCause(exception)
			.addKeyValue("event", "api.error.unhandled")
			.addKeyValue("path", request.getRequestURI())
			.addKeyValue("status", 500)
			.addKeyValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR)
			.addKeyValue("reason", exception.getMessage())
			.log("api.error.unhandled");
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR, "Unexpected server error."));
	}
}
