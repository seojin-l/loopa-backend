package com.example.loopa.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {
    private  static final String TOKEN_TYPE_CLAIM="type";
    private static final String ACCESS_TOKEN_TYPE="ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }


    //로그인 성공 시 access token생성
    public String createAccessToken(Long userId, String email) {
        return createToken(userId, email, ACCESS_TOKEN_TYPE, accessTokenExpiration);
    }

    public String createRefreshToken(Long userId, String email) {
        return createToken(userId,email,REFRESH_TOKEN_TYPE,refreshTokenExpiration);
    }

    public String createToken(Long userId, String email,String tokenType, long expirationTime) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim(TOKEN_TYPE_CLAIM,tokenType)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey) //1시간 유효
                .compact();
    }

    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    public String getEmail(String token) {
        Claims claims = parseClaims(token);
        return claims.get("email", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAccessToken(String token){
        Claims claims=parseClaims(token);
        return ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM,String.class));
    }

    public boolean isRefreshToken(String token){
        Claims claims=parseClaims(token);
        return REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM,String.class));
    }

    public long getRefreshTokenExpiration(){
        return refreshTokenExpiration;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}