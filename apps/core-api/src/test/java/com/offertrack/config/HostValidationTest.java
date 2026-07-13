package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HostValidationTest {
  @Test
  void acceptsExplicitPublicIpv4AndIpv6WithoutDnsResolution() {
    assertThatCode(
            () -> HostValidation.requireExplicitNonLoopbackHost("192.0.2.10", "host.property"))
        .doesNotThrowAnyException();
    assertThatCode(
            () -> HostValidation.requireExplicitNonLoopbackHost("2001:db8::10", "host.property"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsAmbiguousAndLocalAddressForms() {
    for (String host :
        new String[] {"2130706433", "0127.0.0.1", "127.0.0.1", "[::1]", "::ffff:127.0.0.1"}) {
      assertThatThrownBy(() -> HostValidation.requireExplicitNonLoopbackHost(host, "host.property"))
          .hasMessageContaining("host.property")
          .hasMessageNotContaining(host);
    }
  }
}
