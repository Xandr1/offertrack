package com.offertrack.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwtService;
  private final AuthCookieProperties cookieProperties;
  private final AuthSessionService sessions;
  private final ObjectMapper mapper;

  public JwtAuthenticationFilter(
      JwtService jwtService,
      AuthCookieProperties cookieProperties,
      AuthSessionService sessions,
      ObjectMapper mapper) {
    this.jwtService = jwtService;
    this.cookieProperties = cookieProperties;
    this.sessions = sessions;
    this.mapper = mapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    return (path.startsWith("/auth/") && !path.equals("/auth/logout-all"))
        || path.startsWith("/oauth2/")
        || path.startsWith("/login/oauth2/")
        || path.startsWith("/actuator/");
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {
    Optional<JwtService.AccessClaims> claims = extractToken(request).flatMap(jwtService::verify);
    if (claims.isPresent()) {
      try {
        if (sessions.authenticates(claims.get())) {
          CurrentUser user = new CurrentUser(claims.get().userId(), claims.get().sessionId());
          SecurityContextHolder.getContext()
              .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        }
      } catch (AuthServiceUnavailableException exception) {
        SecurityContextHolder.clearContext();
        AuthenticationErrorWriter.write(mapper, request, response, true);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  public Optional<String> extractToken(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      return Optional.of(authorization.substring(7));
    }
    return cookie(request, cookieProperties.getName());
  }

  public static Optional<String> cookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    return cookies == null
        ? Optional.empty()
        : Arrays.stream(cookies)
            .filter(cookie -> name.equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst();
  }
}
