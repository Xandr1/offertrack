package com.offertrack.settings;

import com.offertrack.auth.CurrentUser;
import com.offertrack.settings.dto.SettingsResponse;
import com.offertrack.settings.dto.UpdateSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SettingsController {
  private final SettingsService settingsService;

  public SettingsController(SettingsService settingsService) {
    this.settingsService = settingsService;
  }

  @GetMapping("/api/settings")
  public SettingsResponse getSettings(@AuthenticationPrincipal CurrentUser currentUser) {
    return SettingsResponse.from(settingsService.getSettings(currentUser.id()));
  }

  @PutMapping("/api/settings")
  public SettingsResponse updateSettings(
      @AuthenticationPrincipal CurrentUser currentUser,
      @Valid @RequestBody UpdateSettingsRequest request) {
    return SettingsResponse.from(settingsService.updateSettings(currentUser.id(), request));
  }
}
