package com.healthassistant.infrastructure.web.security;

import com.healthassistant.domain.event.DeviceId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

class HmacSignature {

    private static final String ALGORITHM = "HmacSHA256";

    public static String calculate(
            String method,
            String path,
            String timestamp,
            String nonce,
            DeviceId deviceId,
            String body,
            byte[] secret
    ) {
        String canonicalString = buildCanonicalString(method, path, timestamp, nonce, deviceId, body);

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret, ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to compute HMAC: " + e.getMessage(), e);
        }
    }

    public static String buildCanonicalString(
            String method,
            String path,
            String timestamp,
            String nonce,
            DeviceId deviceId,
            String body
    ) {
        return String.join("\n",
                method,
                path,
                timestamp,
                nonce,
                deviceId.value(),
                body
        );
    }

    public static boolean verify(String expected, String received) {
        if (expected.length() != received.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < expected.length(); i++) {
            result |= expected.charAt(i) ^ received.charAt(i);
        }
        return result == 0;
    }
}

