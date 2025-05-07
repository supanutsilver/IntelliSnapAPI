package com.example.intelli.security;

import com.example.intelli.service.AwsSecretsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtTokenUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenUtil.class);

    private static final String MAIN_SECRET_NAME = "prod/AppBeta/IntelliSnap"; // The main secret containing multiple keys
    private static final String JWT_SECRET_JSON_KEY = "jwt-secret"; // The key for the JWT secret within the JSON

    @Value("${jwt.token.validity:3600000}") // Default to 1 hour (3600000 ms)
    private long jwtTokenValidity;

    private final AwsSecretsService awsSecretsService;
    private Key signingKey;

    @Autowired
    public JwtTokenUtil(AwsSecretsService awsSecretsService) {
        this.awsSecretsService = awsSecretsService;
    }

    @PostConstruct
    public void init() {
        // Fetch the specific JWT secret value from the JSON stored in MAIN_SECRET_NAME
        String base64EncodedSecret = awsSecretsService.getSecretValueFromJson(MAIN_SECRET_NAME, JWT_SECRET_JSON_KEY);
        if (base64EncodedSecret != null && !base64EncodedSecret.isEmpty()) {
            try {
                // Decode the Base64 encoded secret to get the raw key bytes
                byte[] keyBytes = Base64.getDecoder().decode(base64EncodedSecret);
                this.signingKey = Keys.hmacShaKeyFor(keyBytes);
                logger.info("JWT signing key initialized successfully from AWS Secrets Manager (key: '{}' in secret: '{}', after Base64 decoding).", JWT_SECRET_JSON_KEY, MAIN_SECRET_NAME);
            } catch (IllegalArgumentException e) {
                logger.error("Failed to decode Base64 JWT signing key from AWS Secrets Manager. Key '{}' in secret '{}'. Error: {}", JWT_SECRET_JSON_KEY, MAIN_SECRET_NAME, e.getMessage());
                // Consider throwing an IllegalStateException here if the JWT key is critical for app startup
            }
        } else {
            logger.error("Failed to retrieve JWT signing key from AWS Secrets Manager. Key '{}' not found or empty in secret '{}'.", JWT_SECRET_JSON_KEY, MAIN_SECRET_NAME);
            // Consider throwing an IllegalStateException here if the JWT key is critical for app startup
            // throw new IllegalStateException("JWT signing key could not be initialized.");
        }
    }

    // Retrieve username from jwt token
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    // Retrieve expiration date from jwt token
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    // For retrieving any information from token we will need the secret key
    private Claims getAllClaimsFromToken(String token) {
        if (signingKey == null) {
            logger.error("Signing key has not been initialized. Cannot parse JWT token.");
            throw new IllegalStateException("JWT signing key is not initialized.");
        }
        return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
    }

    // Check if the token has expired
    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    // Generate token for user
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, userDetails.getUsername());
    }

    // Generate token for a given subject (e.g., deviceId for anonymous tokens)
    public String generateToken(String subject) {
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, subject);
    }

    // While creating the token -
    // 1. Define claims of the token, like Issuer, Expiration, Subject, and the ID
    // 2. Sign the JWT using the HS512 algorithm and secret key.
    // 3. According to JWS Compact Serialization(https://tools.ietf.org/html/draft-ietf-jose-json-web-signature-41#section-3.1)
    //    compaction of the JWT to a URL-safe string
    private String doGenerateToken(Map<String, Object> claims, String subject) {
        if (signingKey == null) {
            logger.error("Signing key has not been initialized. Cannot generate JWT token.");
            throw new IllegalStateException("JWT signing key is not initialized.");
        }
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtTokenValidity))
                .signWith(signingKey, SignatureAlgorithm.HS256) // Or HS384, HS512 if your key is strong enough
                .compact();
    }

    // Validate token
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}