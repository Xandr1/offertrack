package com.offertrack.applications;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

final class GoogleAiServiceIdentityTokenProvider implements AiServiceIdentityTokenProvider {
  private final Object applicationDefaultCredentialsLock = new Object();
  private final ConcurrentMap<String, CachedAudienceCredentials> credentialsByAudience =
      new ConcurrentHashMap<>();
  private final Supplier<IdTokenProvider> applicationDefaultCredentialsLoader;

  private volatile IdTokenProvider applicationDefaultCredentials;

  GoogleAiServiceIdentityTokenProvider() {
    this(GoogleAiServiceIdentityTokenProvider::loadAdcIdentityTokenProvider);
  }

  GoogleAiServiceIdentityTokenProvider(
      Supplier<IdTokenProvider> applicationDefaultCredentialsLoader) {
    this.applicationDefaultCredentialsLoader = applicationDefaultCredentialsLoader;
  }

  @Override
  public String getToken(String audience) {
    if (audience == null || audience.isBlank()) {
      throw new AiServiceIdentityTokenException();
    }

    try {
      return credentialsByAudience
          .computeIfAbsent(audience, this::createAudienceCredentials)
          .getToken();
    } catch (AiServiceIdentityTokenException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new AiServiceIdentityTokenException();
    }
  }

  private CachedAudienceCredentials createAudienceCredentials(String audience) {
    IdTokenCredentials credentials =
        IdTokenCredentials.newBuilder()
            .setIdTokenProvider(loadApplicationDefaultCredentials())
            .setTargetAudience(audience)
            .setOptions(List.of(IdTokenProvider.Option.FORMAT_FULL))
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

      current = applicationDefaultCredentialsLoader.get();
      if (current == null) {
        throw new AiServiceIdentityTokenException();
      }
      applicationDefaultCredentials = current;
      return current;
    }
  }

  private static IdTokenProvider loadAdcIdentityTokenProvider() {
    try {
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
      if (credentials instanceof IdTokenProvider idTokenProvider) {
        return idTokenProvider;
      }
      throw new AiServiceIdentityTokenException();
    } catch (IOException exception) {
      throw new AiServiceIdentityTokenException();
    }
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
