package com.offertrack.auth;

/** A validated, case-sensitive OIDC subject. Email is never its lookup key. */
public record GoogleIdentity(String subject, String email, String name, boolean emailVerified) {
  public GoogleIdentity {
    if (subject == null
        || subject.isBlank()
        || subject.length() > 255
        || subject.chars().anyMatch(character -> character == 0 || character > 127)) {
      throw new IllegalArgumentException("Invalid Google identity");
    }
  }

  @Override
  public String toString() {
    return "GoogleIdentity[redacted]";
  }
}
