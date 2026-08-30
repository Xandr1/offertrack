package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class DependencyHealthAuthenticationFilterTest {
  private static final String HEALTH_KEY =
      "dependency-health-key-which-is-long-enough-and-distinct";

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatesTheDependencyEndpointWithItsDedicatedKey() throws Exception {
    DependencyHealthAuthenticationFilter filter = filterWithKey(HEALTH_KEY);
    MockHttpServletRequest request = dependencyRequest();
    request.addHeader(DependencyHealthAuthenticationFilter.HEADER, HEALTH_KEY);

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication())
        .isNotNull()
        .extracting("principal")
        .isEqualTo("dependency-health");
    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
        .extracting("authority")
        .containsExactly(DependencyHealthAuthenticationFilter.AUTHORITY);
  }

  @Test
  void doesNotAuthenticateWithTheAiInternalKey() throws Exception {
    DependencyHealthAuthenticationFilter filter = filterWithKey(HEALTH_KEY);
    MockHttpServletRequest request = dependencyRequest();
    request.addHeader(
        DependencyHealthAuthenticationFilter.HEADER,
        "ai-service-internal-key-which-is-long-enough");

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void doesNotAuthenticateMissingOrEmptyConfiguration() throws Exception {
    DependencyHealthAuthenticationFilter filter = filterWithKey("");
    MockHttpServletRequest request = dependencyRequest();
    request.addHeader(DependencyHealthAuthenticationFilter.HEADER, "");

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private static DependencyHealthAuthenticationFilter filterWithKey(String key) {
    return new DependencyHealthAuthenticationFilter(
        new MockEnvironment().withProperty("app.management.dependency-health-key", key));
  }

  private static MockHttpServletRequest dependencyRequest() {
    return new MockHttpServletRequest("GET", DependencyHealthAuthenticationFilter.PATH);
  }
}
