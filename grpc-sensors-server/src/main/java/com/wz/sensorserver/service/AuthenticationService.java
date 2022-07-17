package com.wz.sensorserver.service;

import com.wz.sensorserver.exception.AuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public class AuthenticationService {
    private final String secret;
    private final Key hmacKey;

    public AuthenticationService(String secret) {
        this.secret = secret;
        hmacKey = new SecretKeySpec(Base64.getDecoder().decode(secret),
                SignatureAlgorithm.HS256.getJcaName());
    }

    /**
     * Generates token with given key
     *
     * @param claims payload
     * @return generated token
     */
    public String generateToken(Map<String, Object> claims) {
        String token = Jwts.builder()
                .addClaims(claims)
                .setIssuedAt(Date.from(Instant.now()))
                .signWith(hmacKey)
                .compact();
        return Base64
                .getEncoder()
                .encodeToString(token.getBytes());

    }

    /**
     * Validates given JWT token and returns sensorId
     *
     * @param token token to decrypt
     * @return claims
     */
    public Claims validateToken(String token) {
        if (token == null)
            throw new AuthenticationException("Invalid token");
        try {
            String decodedToken = new String(Base64
                    .getDecoder()
                    .decode(token));
            Jws<Claims> claimsJws = Jwts.parserBuilder()
                    .setSigningKey(hmacKey)
                    .build()
                    .parseClaimsJws(decodedToken);
            return claimsJws.getBody();
        } catch (Exception e) {
            throw new AuthenticationException("Invalid token");
        }
    }
}
