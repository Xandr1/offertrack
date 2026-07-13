package com.offertrack.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {
  @NotEmpty
  private List<@NotBlank String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));

  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  @AssertTrue(message = "credentialed CORS requires explicit origins")
  public boolean isCredentialedOriginsValid() {
    return allowedOrigins != null
        && !allowedOrigins.isEmpty()
        && allowedOrigins.stream()
            .allMatch(origin -> StringUtils.hasText(origin) && !"*".equals(origin.trim()));
  }
}
