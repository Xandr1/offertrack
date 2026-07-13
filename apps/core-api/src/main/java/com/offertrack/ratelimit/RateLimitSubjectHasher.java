package com.offertrack.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class RateLimitSubjectHasher {
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final SecretKeySpec key;

  RateLimitSubjectHasher(RateLimitProperties properties) {
    this.key =
        new SecretKeySpec(
            properties.getKeySecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
  }

  String hash(String subject) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(key);
      return HexFormat.of().formatHex(mac.doFinal(subject.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Could not derive rate-limit subject key", exception);
    }
  }
}
