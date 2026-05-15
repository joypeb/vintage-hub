package com.joypeb.vintagehub.auth;

public class PasswordHashApiDisabledException extends RuntimeException {

	PasswordHashApiDisabledException() {
		super("비밀번호 해시 생성 API가 비활성화되어 있습니다.");
	}
}
