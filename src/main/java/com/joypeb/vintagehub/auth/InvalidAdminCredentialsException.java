package com.joypeb.vintagehub.auth;

public class InvalidAdminCredentialsException extends RuntimeException {

	InvalidAdminCredentialsException() {
		super("관리자 인증 정보가 올바르지 않습니다.");
	}
}
