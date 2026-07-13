package com.offertrack.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;

public final class ProtectedProfiles {
  public static final Set<String> NAMES = Set.of("prod", "production", "stage", "staging");

  private ProtectedProfiles() {}

  public static boolean isProtected(Environment environment) {
    String[] activeProfiles = environment.getActiveProfiles();
    String[] effectiveProfiles =
        activeProfiles.length == 0 ? environment.getDefaultProfiles() : activeProfiles;
    return Arrays.stream(effectiveProfiles)
        .map(profile -> profile.toLowerCase(Locale.ROOT))
        .anyMatch(NAMES::contains);
  }
}
