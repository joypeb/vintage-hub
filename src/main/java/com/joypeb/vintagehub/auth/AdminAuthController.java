package com.joypeb.vintagehub.auth;

import com.joypeb.vintagehub.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
class AdminAuthController {

	private final AdminAuthService adminAuthService;

	AdminAuthController(AdminAuthService adminAuthService) {
		this.adminAuthService = adminAuthService;
	}

	@PostMapping("/login")
	ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
		AdminAuthService.LoginResult result = adminAuthService.login(request.username(), request.password());
		return ResponseEntity.ok(ApiResponse.success(LoginResponse.from(result)));
	}

	@PostMapping("/password-hash")
	ResponseEntity<ApiResponse<PasswordHashResponse>> createPasswordHash(@RequestBody PasswordHashRequest request) {
		return ResponseEntity.ok(
			ApiResponse.success(new PasswordHashResponse(adminAuthService.encodePassword(request.password()))));
	}

	record LoginRequest(String username, String password) {
	}

	record LoginResponse(String accessToken, String tokenType, long expiresIn) {

		private static LoginResponse from(AdminAuthService.LoginResult result) {
			return new LoginResponse(result.accessToken(), result.tokenType(), result.expiresIn());
		}
	}

	record PasswordHashRequest(String password) {
	}

	record PasswordHashResponse(String passwordHash) {
	}
}
