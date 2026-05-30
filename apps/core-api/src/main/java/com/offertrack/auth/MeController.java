package com.offertrack.auth;

import com.offertrack.auth.dto.CurrentUserResponse;
import com.offertrack.users.User;
import com.offertrack.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MeController {
  private final UserRepository userRepository;

  public MeController(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @GetMapping("/api/me")
  public CurrentUserResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
    User user =
        userRepository
            .findById(currentUser.id())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User linked to this token no longer exists"));

    return new CurrentUserResponse(user.id(), user.email(), user.name());
  }
}
