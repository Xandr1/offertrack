package com.offertrack.applications.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpOrHttpsUrlValidator implements ConstraintValidator<HttpOrHttpsUrl, String> {
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }

    try {
      URI uri = new URI(value);
      String scheme = uri.getScheme();

      return uri.isAbsolute()
          && scheme != null
          && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
          && uri.getHost() != null
          && !uri.getHost().isBlank();
    } catch (URISyntaxException exception) {
      return false;
    }
  }
}
