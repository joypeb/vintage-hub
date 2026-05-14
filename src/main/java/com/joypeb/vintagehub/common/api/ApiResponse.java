package com.joypeb.vintagehub.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
		return new ApiResponse<>(false, null, new ApiError(errorCode.code(), errorCode.description(), message));
	}

	public record ApiError(String code, String description, String message) {
	}
}
