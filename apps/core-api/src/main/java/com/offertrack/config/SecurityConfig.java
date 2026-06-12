package com.offertrack.config;

import com.offertrack.auth.CookieOAuth2AuthorizationRequestRepository;
import com.offertrack.auth.JwtAuthenticationFilter;
import com.offertrack.auth.NoopOAuth2AuthorizedClientRepository;
import com.offertrack.auth.OAuth2AuthorizationRequestCookieClearingFailureHandler;
import com.offertrack.auth.OAuth2AuthorizationRequestCookieClearingSuccessHandler;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CookieOAuth2AuthorizationRequestRepository oauth2AuthorizationRequestRepository,
      OAuth2AuthorizedClientRepository oauth2AuthorizedClientRepository,
      OAuth2AuthorizationRequestCookieClearingSuccessHandler oauth2SuccessHandler,
      OAuth2AuthorizationRequestCookieClearingFailureHandler oauth2FailureHandler,
      ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository)
      throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exception ->
                exception.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
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
                        "/login")
                    .permitAll()
                    .requestMatchers(
                        "/auth/oauth2/google/start",
                        "/oauth2/authorization/**",
                        "/login/oauth2/code/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated());

    if (clientRegistrationRepository.getIfAvailable() != null) {
      http.oauth2Login(
          oauth2 ->
              oauth2
                  .authorizationEndpoint(
                      authorization ->
                          authorization.authorizationRequestRepository(
                              oauth2AuthorizationRequestRepository))
                  .authorizedClientRepository(oauth2AuthorizedClientRepository)
                  .successHandler(oauth2SuccessHandler)
                  .failureHandler(oauth2FailureHandler));
    }

    return http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public CookieOAuth2AuthorizationRequestRepository oauth2AuthorizationRequestRepository(
      @Value("${app.oauth.authorization-request-cookie-signing-secret}") String signingSecret,
      @Value("${app.jwt.secret}") String jwtSecret) {
    String resolvedSigningSecret = StringUtils.hasText(signingSecret) ? signingSecret : jwtSecret;
    return new CookieOAuth2AuthorizationRequestRepository(resolvedSigningSecret);
  }

  @Bean
  public OAuth2AuthorizedClientRepository oauth2AuthorizedClientRepository() {
    return new NoopOAuth2AuthorizedClientRepository();
  }

  @Bean
  public OAuth2AuthorizationRequestCookieClearingSuccessHandler oauth2SuccessHandler(
      CookieOAuth2AuthorizationRequestRepository oauth2AuthorizationRequestRepository) {
    return new OAuth2AuthorizationRequestCookieClearingSuccessHandler(
        oauth2AuthorizationRequestRepository);
  }

  @Bean
  public OAuth2AuthorizationRequestCookieClearingFailureHandler oauth2FailureHandler(
      CookieOAuth2AuthorizationRequestRepository oauth2AuthorizationRequestRepository,
      @Value("${app.web-url}") String appWebUrl) {
    return new OAuth2AuthorizationRequestCookieClearingFailureHandler(
        oauth2AuthorizationRequestRepository, appWebUrl);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    configuration.setAllowedOrigins(List.of("http://localhost:3000"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Content-Type", "Authorization"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    return source;
  }
}
