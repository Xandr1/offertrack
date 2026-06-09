package com.offertrack;

import static com.offertrack.jooq.generated.tables.UserSettings.USER_SETTINGS;
import static org.assertj.core.api.Assertions.assertThat;

import com.offertrack.settings.SettingsService;
import com.offertrack.settings.UserSettings;
import com.offertrack.settings.dto.UpdateSettingsRequest;
import com.offertrack.users.UserRepository;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SettingsServiceIntegrationTest {
  @Autowired private SettingsService settingsService;
  @Autowired private DSLContext dsl;
  @Autowired private UserRepository userRepository;

  @BeforeEach
  void cleanDatabase() {
    dsl.execute("delete from user_settings");
    dsl.execute("delete from application_interviews");
    dsl.execute("delete from job_applications");
    dsl.execute("delete from users");
  }

  @Test
  void getSettingsReturnsDefaultsWhenNoRowExists() {
    UUID userId = createUser("defaults@example.com");

    UserSettings settings = settingsService.getSettings(userId);

    assertThat(settings.followUpAfterApplyingDays()).isEqualTo(7);
    assertThat(settings.upcomingInterviewDays()).isEqualTo(7);
    assertThat(settings.followUpAfterInterviewDays()).isEqualTo(2);
    assertThat(settings.targetRole()).isNull();
    assertThat(dsl.fetchCount(USER_SETTINGS, USER_SETTINGS.USER_ID.eq(userId))).isZero();
  }

  @Test
  void updateSettingsCreatesRow() {
    UUID userId = createUser("create-settings@example.com");

    UserSettings settings =
        settingsService.updateSettings(
            userId, new UpdateSettingsRequest(10, 14, 4, "Platform Engineer"));

    assertThat(settings.followUpAfterApplyingDays()).isEqualTo(10);
    assertThat(settings.upcomingInterviewDays()).isEqualTo(14);
    assertThat(settings.followUpAfterInterviewDays()).isEqualTo(4);
    assertThat(settings.targetRole()).isEqualTo("Platform Engineer");
    assertThat(dsl.fetchCount(USER_SETTINGS, USER_SETTINGS.USER_ID.eq(userId))).isEqualTo(1);
  }

  @Test
  void updateSettingsUpdatesExistingRow() {
    UUID userId = createUser("update-settings@example.com");
    settingsService.updateSettings(userId, new UpdateSettingsRequest(10, 14, 4, "Platform"));

    UserSettings settings =
        settingsService.updateSettings(userId, new UpdateSettingsRequest(3, 5, 1, "Backend"));

    assertThat(settings.followUpAfterApplyingDays()).isEqualTo(3);
    assertThat(settings.upcomingInterviewDays()).isEqualTo(5);
    assertThat(settings.followUpAfterInterviewDays()).isEqualTo(1);
    assertThat(settings.targetRole()).isEqualTo("Backend");
    assertThat(dsl.fetchCount(USER_SETTINGS, USER_SETTINGS.USER_ID.eq(userId))).isEqualTo(1);
  }

  @Test
  void blankTargetRoleStoresNull() {
    UUID userId = createUser("blank-target@example.com");

    UserSettings settings =
        settingsService.updateSettings(userId, new UpdateSettingsRequest(7, 7, 2, "   "));

    assertThat(settings.targetRole()).isNull();
    assertThat(
            dsl.select(USER_SETTINGS.TARGET_ROLE)
                .from(USER_SETTINGS)
                .where(USER_SETTINGS.USER_ID.eq(userId))
                .fetchSingle(USER_SETTINGS.TARGET_ROLE))
        .isNull();
  }

  @Test
  void settingsAreIsolatedByUser() {
    UUID userId = createUser("owner-settings@example.com");
    UUID otherUserId = createUser("other-settings@example.com");

    settingsService.updateSettings(userId, new UpdateSettingsRequest(12, 6, 5, "Owner Role"));

    UserSettings otherSettings = settingsService.getSettings(otherUserId);
    assertThat(otherSettings.followUpAfterApplyingDays()).isEqualTo(7);
    assertThat(otherSettings.upcomingInterviewDays()).isEqualTo(7);
    assertThat(otherSettings.followUpAfterInterviewDays()).isEqualTo(2);
    assertThat(otherSettings.targetRole()).isNull();

    settingsService.updateSettings(otherUserId, new UpdateSettingsRequest(2, 3, 1, "Other Role"));

    UserSettings ownerSettings = settingsService.getSettings(userId);
    assertThat(ownerSettings.followUpAfterApplyingDays()).isEqualTo(12);
    assertThat(ownerSettings.upcomingInterviewDays()).isEqualTo(6);
    assertThat(ownerSettings.followUpAfterInterviewDays()).isEqualTo(5);
    assertThat(ownerSettings.targetRole()).isEqualTo("Owner Role");
  }

  private UUID createUser(String email) {
    return userRepository.createUser(email, "hash", "Test User").id();
  }
}
