package com.offertrack.auth;

import com.offertrack.auth.dto.AuthResponse;
import com.offertrack.auth.dto.LoginRequest;
import com.offertrack.auth.dto.RegisterRequest;
import com.offertrack.users.User;
import com.offertrack.users.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private final UserRepository userRepository;
  private final PasswordService passwordService;
  private final JwtService jwtService;

  public AuthService(
      UserRepository userRepository, PasswordService passwordService, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordService = passwordService;
    this.jwtService = jwtService;
  }

  public AuthResult register(RegisterRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();
    String passwordHash = passwordService.hash(request.password());

    try {
      User user = userRepository.createUser(normalizedEmail, passwordHash, request.name());

      return buildAuthResult(user);
    } catch (DuplicateKeyException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this email already exists");
    }
  }

  public AuthResult login(LoginRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();

    User user =
        userRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"));

    if (!passwordService.matches(request.password(), user.passwordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    return buildAuthResult(user);
  }

  private AuthResult buildAuthResult(User user) {
    String accessToken = jwtService.generateAccessToken(user.id(), user.email());

    AuthResponse response =
        new AuthResponse(new AuthResponse.UserSummary(user.id(), user.email(), user.name()));

    return new AuthResult(accessToken, response);
  }

  public record AuthResult(String accessToken, AuthResponse response) {}
}
