package com.crosschecknews.api.service;

import com.crosschecknews.api.dto.LoginRequest;
import com.crosschecknews.api.dto.LoginResponse;
import com.crosschecknews.api.exception.InvalidCredentialsException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class AuthService {

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    public LoginResponse login(LoginRequest request) {
        if (!adminUsername.equals(request.getUsername()) ||
            !adminPassword.equals(request.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = generateToken(request.getUsername());
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expirationMs / 1000)
                .build();
    }

    private String generateToken(String username) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("role", "ADMIN")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }
}
