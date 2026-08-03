package com.offertrack.applications;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdToken;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class GoogleAiServiceIdentityTokenProvider implements AiServiceIdentityTokenProvider {
  private final Object applicationDefaultCredentialsLock = new Object();
  private final ConcurrentMap<String, CachedAudienceCredentials> credentialsByAudience =
      new ConcurrentHashMap<>();
  private final ApplicationDefaultCredentialsLoader applicationDefaultCredentialsLoader;

  private volatile IdTokenProvider applicationDefaultCredentials;

  GoogleAiServiceIdentityTokenProvider() {
    this(GoogleCredentials::getApplicationDefault);
  }

  GoogleAiServiceIdentityTokenProvider(
      ApplicationDefaultCredentialsLoader applicationDefaultCredentialsLoader) {
    this.applicationDefaultCredentialsLoader = applicationDefaultCredentialsLoader;
  }

  @Override
  public String getToken(String audience) {
    if (audience == null || audience.isBlank()) {
      throw new AiServiceIdentityTokenException();
    }

    return credentialsByAudience
        .computeIfAbsent(audience, this::createAudienceCredentials)
        .getToken();
  }

  private CachedAudienceCredentials createAudienceCredentials(String audience) {
    IdTokenCredentials credentials =
        IdTokenCredentials.newBuilder()
            .setIdTokenProvider(loadApplicationDefaultCredentials())
            .setTargetAudience(audience)
            .build();
    return new CachedAudienceCredentials(credentials);
  }

  private IdTokenProvider loadApplicationDefaultCredentials() {
    IdTokenProvider current = applicationDefaultCredentials;
    if (current != null) {
      return current;
    }

    synchronized (applicationDefaultCredentialsLock) {
      current = applicationDefaultCredentials;
      if (current != null) {
        return current;
      }

      GoogleCredentials credentials;
      try {
        credentials = applicationDefaultCredentialsLoader.load();
      } catch (IOException exception) {
        throw new AiServiceIdentityTokenException();
      }
      if (!(credentials instanceof IdTokenProvider idTokenProvider)) {
        throw new AiServiceIdentityTokenException();
      }
      current = validatingProvider(idTokenProvider);
      applicationDefaultCredentials = current;
      return current;
    }
  }

  private static IdTokenProvider validatingProvider(IdTokenProvider provider) {
    return (audience, options) -> {
      IdToken token = provider.idTokenWithAudience(audience, options);
      if (token == null || token.getTokenValue() == null || token.getTokenValue().isBlank()) {
        throw new AiServiceIdentityTokenException();
      }
      return token;
    };
  }

  @FunctionalInterface
  interface ApplicationDefaultCredentialsLoader {
    GoogleCredentials load() throws IOException;
  }

  private static final class CachedAudienceCredentials {
    private final IdTokenCredentials credentials;

    private CachedAudienceCredentials(IdTokenCredentials credentials) {
      this.credentials = credentials;
    }

    private synchronized String getToken() {
      try {
        credentials.refreshIfExpired();
        AccessToken token = credentials.getAccessToken();
        if (token == null || token.getTokenValue() == null || token.getTokenValue().isBlank()) {
          throw new AiServiceIdentityTokenException();
        }
        return token.getTokenValue();
      } catch (IOException exception) {
        throw new AiServiceIdentityTokenException();
      }
    }
  }
}
