package com.healthassistant.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

final class HmacSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSignature() {
        throw new UnsupportedOperationException("Utility class");
    }

    static String calculate(String canonicalString, byte[] secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret, ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalArgumentException("Failed to compute HMAC: " + e.getMessage(), e);
        }
    }

    static boolean verify(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }

        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
