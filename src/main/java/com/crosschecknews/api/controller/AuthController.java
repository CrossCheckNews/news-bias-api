package com.crosschecknews.api.controller;

import com.crosschecknews.api.dto.LoginRequest;
import com.crosschecknews.api.dto.LoginResponse;
import com.crosschecknews.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "관리자 인증 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "관리자 로그인", description = "관리자 계정으로 로그인하고 JWT 토큰을 발급받습니다.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
