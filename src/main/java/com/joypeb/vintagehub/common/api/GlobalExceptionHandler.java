package com.joypeb.vintagehub.common.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
		return ResponseEntity.badRequest()
			.body(ApiResponse.error(ErrorCode.INVALID_REQUEST, exception.getMessage()));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiResponse.error(ErrorCode.NOT_FOUND, exception.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR, "Unexpected server error."));
	}
}
