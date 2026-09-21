package com.offertrack.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

final class RefreshTokenCodec {
  private static final SecureRandom RANDOM = new SecureRandom();

  private RefreshTokenCodec() {}

  static String generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static boolean isValidShape(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return false;
    byte[] decoded = Base64.getUrlDecoder().decode(token);
    return decoded.length == 32
        && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(token);
  }

  static byte[] hash(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }
}
