package com.offertrack.config;

import static com.offertrack.config.ConfigurationRuleSupport.invalid;
import static com.offertrack.config.ConfigurationRuleSupport.requireText;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.TrustManagerFactory;

final class RedisTlsTrustMaterialValidator {
  static final String PROPERTY = "spring.ssl.bundle.pem.offertrack-redis.truststore.certificate";

  private static final Pattern CERTIFICATE_BLOCK =
      Pattern.compile("-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", Pattern.DOTALL);

  private RedisTlsTrustMaterialValidator() {}

  static void validate(String trustMaterial) {
    requireText(trustMaterial, PROPERTY);

    try {
      List<X509Certificate> certificates = parseCertificates(trustMaterial);
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null, null);
      for (int index = 0; index < certificates.size(); index++) {
        trustStore.setCertificateEntry("memorystore-ca-" + index, certificates.get(index));
      }

      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(trustStore);
      if (trustManagerFactory.getTrustManagers().length == 0) {
        invalid(PROPERTY);
      }
    } catch (Exception exception) {
      throw ConfigurationRuleSupport.invalidException(PROPERTY);
    }
  }

  private static List<X509Certificate> parseCertificates(String trustMaterial) throws Exception {
    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
    Matcher matcher = CERTIFICATE_BLOCK.matcher(trustMaterial);
    List<X509Certificate> certificates = new ArrayList<>();
    int previousEnd = 0;

    while (matcher.find()) {
      if (!trustMaterial.substring(previousEnd, matcher.start()).isBlank()) {
        invalid(PROPERTY);
      }

      X509Certificate certificate =
          (X509Certificate)
              certificateFactory.generateCertificate(
                  new ByteArrayInputStream(matcher.group().getBytes(StandardCharsets.US_ASCII)));
      certificate.checkValidity();
      if (certificate.getBasicConstraints() < 0) {
        invalid(PROPERTY);
      }
      certificates.add(certificate);
      previousEnd = matcher.end();
    }

    if (certificates.isEmpty() || !trustMaterial.substring(previousEnd).isBlank()) {
      invalid(PROPERTY);
    }

    return certificates;
  }
}
