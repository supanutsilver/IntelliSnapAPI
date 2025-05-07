package com.example.intelli.controller;

import com.example.intelli.security.JwtTokenUtil;
import com.example.intelli.service.AwsSecretsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String MAIN_SECRET_NAME = "prod/AppBeta/IntelliSnap"; // The main secret name
    private static final String MOBILE_APP_KEY_JSON_KEY = "mobile-app-key"; // The key for the mobile app key within the JSON

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private AwsSecretsService secretsService;

    @PostMapping("/anonymous-token")
    public ResponseEntity<?> getAnonymousToken(
            @RequestHeader(value = "X-Mobile-App-Key", required = false) String mobileAppKey,
            @RequestParam String deviceId) {
        String expectedKey = secretsService.getSecretValueFromJson(MAIN_SECRET_NAME, MOBILE_APP_KEY_JSON_KEY);
        if (expectedKey == null || !expectedKey.equals(mobileAppKey)) {
            // It's good practice to log such failures, but be careful not to log the expectedKey or mobileAppKey itself unless in debug mode
            // logger.warn("Forbidden: Invalid mobile app key attempt for deviceId: {}", deviceId);
            return ResponseEntity.status(403).body("Forbidden: Invalid mobile app key");
        }
        String token = jwtTokenUtil.generateToken(deviceId); // 1 hour
        Map<String, String> result = new HashMap<>();
        result.put("token", token);
        return ResponseEntity.ok(result);
    }
}
