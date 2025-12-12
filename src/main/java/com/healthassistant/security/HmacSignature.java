package com.healthassistant.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

class HmacSignature {

    private static final String ALGORITHM = "HmacSHA256";

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
        return expected != null && expected.equals(actual);
    }
}
