package com.offertrack.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertrack.applications.AiServiceProperties;
import com.offertrack.auth.AuthCookieProperties;
import com.offertrack.auth.AuthService;
import com.offertrack.auth.CookieOAuth2AuthorizationRequestRepository;
import com.offertrack.auth.CookieService;
import com.offertrack.auth.CsrfTokenInvalidationService;
import com.offertrack.auth.GoogleOAuth2SuccessHandler;
import com.offertrack.auth.JwtAuthenticationFilter;
import com.offertrack.auth.JwtProperties;
import com.offertrack.auth.NoopOAuth2AuthorizedClientRepository;
import com.offertrack.auth.OAuth2AuthorizationRequestCookieClearingFailureHandler;
import com.offertrack.auth.OAuthProperties;
import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({
  JwtProperties.class,
  AuthCookieProperties.class,
  OAuthProperties.class,
  CorsProperties.class,
  WebProperties.class,
  AiServiceProperties.class
})
public class SecurityConfig {
  private static final List<String> CORS_METHODS =
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
  private static final List<String> CORS_HEADERS =
      List.of("Content-Type", "Authorization", "X-XSRF-TOKEN", "X-Request-Id");
  private static final List<String> CORS_EXPOSED_HEADERS = List.of("Retry-After", "X-Request-Id");

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final AuthCookieProperties cookieProperties;
  private final Environment environment;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      AuthCookieProperties cookieProperties,
      Environment environment) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.cookieProperties = cookieProperties;
    this.environment = environment;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CorsConfigurationSource corsConfigurationSource,
      CsrfTokenRepository csrfTokenRepository,
      ObjectMapper objectMapper,
      CookieOAuth2AuthorizationRequestRepository oauth2AuthorizationRequestRepository,
      OAuth2AuthorizedClientRepository oauth2AuthorizedClientRepository,
      GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler,
      OAuth2AuthorizationRequestCookieClearingFailureHandler oauth2FailureHandler)
      throws Exception {
    XorCsrfTokenRequestAttributeHandler csrfRequestHandler =
        new XorCsrfTokenRequestAttributeHandler();

    http.cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(csrfRequestHandler)
                    .requireCsrfProtectionMatcher(new BrowserCsrfRequestMatcher(cookieProperties)))
        .addFilterBefore(
            new DependencyHealthAuthenticationFilter(environment),
            UsernamePasswordAuthenticationFilter.class)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN))
                    .accessDeniedHandler(new JsonCsrfAccessDeniedHandler(objectMapper)))
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                    .permitAll()
                    .requestMatchers(
                        "/auth/register",
                        "/auth/login",
                        "/auth/logout",
                        "/auth/email/verify",
                        "/auth/email/verification/resend",
                        "/auth/password/forgot",
                        "/auth/password/reset",
                        "/auth/csrf")
                    .permitAll()
                    .requestMatchers(
                        "/auth/oauth2/google/start",
                        "/oauth2/authorization/**",
                        "/login/oauth2/code/**")
                    .permitAll()
                    .requestMatchers(DependencyHealthAuthenticationFilter.PATH)
                    .hasAuthority(DependencyHealthAuthenticationFilter.AUTHORITY)
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness")
                    .permitAll()
                    .anyRequest()
                    .authenticated());

    http.headers(
        headers -> {
          headers
              .contentTypeOptions(Customizer.withDefaults())
              .frameOptions(frame -> frame.deny())
              .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
              .addHeaderWriter(
                  new StaticHeadersWriter(
                      "Permissions-Policy",
                      "camera=(), microphone=(), geolocation=(), payment=(), usb=()"));

          if (ProtectedProfiles.isProtected(environment)) {
            headers.httpStrictTransportSecurity(
                hsts ->
                    hsts.includeSubDomains(true)
                        .preload(false)
                        .maxAgeInSeconds(Duration.ofDays(365).toSeconds()));
          } else {
            headers.httpStrictTransportSecurity(hsts -> hsts.disable());
          }
        });

    http.oauth2Login(
        oauth2 ->
            oauth2
                .authorizationEndpoint(
                    authorization ->
                        authorization.authorizationRequestRepository(
                            oauth2AuthorizationRequestRepository))
                .authorizedClientRepository(oauth2AuthorizedClientRepository)
                .successHandler(googleOAuth2SuccessHandler)
                .failureHandler(oauth2FailureHandler));

    return http.addFilterBefore(jwtAuthenticationFilter, CsrfFilter.class).build();
  }

  @Bean
  public CookieCsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
    repository.setCookieName("XSRF-TOKEN");
    repository.setHeaderName("X-XSRF-TOKEN");
    repository.setCookiePath("/");
    repository.setCookieCustomizer(
        cookie ->
            cookie
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite()));
    return repository;
  }

  @Bean
  public CsrfTokenInvalidationService csrfTokenInvalidationService(
      CsrfTokenRepository csrfTokenRepository) {
    return new CsrfTokenInvalidationService(csrfTokenRepository);
  }

  @Bean
  public CookieOAuth2AuthorizationRequestRepository oauth2AuthorizationRequestRepository(
      OAuthProperties oauthProperties, JwtProperties jwtProperties) {
    String signingSecret = oauthProperties.getAuthorizationRequestCookieSigningSecret();
    String resolvedSigningSecret =
        StringUtils.hasText(signingSecret) ? signingSecret : jwtProperties.getSecret();
    return new CookieOAuth2AuthorizationRequestRepository(resolvedSigningSecret);
  }

  @Bean
  public OAuth2AuthorizedClientRepository oauth2AuthorizedClientRepository() {
    return new NoopOAuth2AuthorizedClientRepository();
  }

  @Bean
  public GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler(
      AuthService authService,
      CookieService cookieService,
      CsrfTokenInvalidationService csrfTokenInvalidationService,
      CookieOAuth2AuthorizationRequestRepository oauth2AuthorizationRequestRepository,
      WebProperties webProperties) {
    return new GoogleOAuth2SuccessHandler(
        authService,
        cookieService,
        csrfTokenInvalidationService,
        oauth2AuthorizationRequestRepository,
        webProperties.getUrl());
  }

  @Bean
  public OAuth2AuthorizationRequestCookieClearingFailureHandler oauth2FailureHandler(
      CookieOAuth2AuthorizationRequestRepository oauth2AuthorizationRequestRepository,
      WebProperties webProperties) {
    return new OAuth2AuthorizationRequestCookieClearingFailureHandler(
        oauth2AuthorizationRequestRepository, webProperties.getUrl());
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
    configuration.setAllowedMethods(CORS_METHODS);
    configuration.setAllowedHeaders(CORS_HEADERS);
    configuration.setExposedHeaders(CORS_EXPOSED_HEADERS);
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
