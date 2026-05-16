package com.joypeb.vintagehub.common.api;

public enum ErrorCode {

	INVALID_REQUEST("ERROR_001", "잘못된 요청입니다."),
	NOT_FOUND("ERROR_002", "요청한 리소스를 찾을 수 없습니다."),
	UNAUTHORIZED("ERROR_003", "인증이 필요합니다."),
	FORBIDDEN("ERROR_004", "권한이 부족합니다."),
	CONFLICT("ERROR_005", "요청한 리소스 상태와 충돌합니다."),
	INTERNAL_SERVER_ERROR("ERROR_999", "서버 내부 오류입니다.");

	private final String code;
	private final String description;

	ErrorCode(String code, String description) {
		this.code = code;
		this.description = description;
	}

	public String code() {
		return code;
	}

	public String description() {
		return description;
	}
}
