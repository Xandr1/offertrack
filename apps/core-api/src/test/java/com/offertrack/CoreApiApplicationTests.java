package com.offertrack;

import static org.assertj.core.api.Assertions.assertThat;

import com.offertrack.applications.ApplicationStage;
import com.offertrack.auth.dto.LoginRequest;
import com.offertrack.errors.ApiErrorResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class CoreApiApplicationTests {
  @Autowired private JsonMapper mapper;
  @Autowired private ApplicationContext context;

  @Test
  void contextLoads() {}

  @Test
  void jackson3AutoConfigurationKeepsTheJsonApiContract() throws Exception {
    assertThat(context.getBean(ObjectMapper.class)).isSameAs(mapper);
    assertThat(
            context.getBeanNamesForType(
                Class.forName("com.fasterxml.jackson.databind.ObjectMapper")))
        .isEmpty();

    var timestamp = OffsetDateTime.parse("2026-10-06T12:30:00Z");
    var error =
        new ApiErrorResponse(
            401, "AUTHENTICATION_REQUIRED", "Authentication required.", "/api/me", timestamp, null);
    var json = mapper.readTree(mapper.writeValueAsString(error));
    assertThat(OffsetDateTime.parse(json.get("timestamp").asText())).isEqualTo(timestamp);
    assertThat(json.has("fieldErrors")).isFalse();
    assertThat(json.get("status").asInt()).isEqualTo(401);
    assertThat(json.get("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
    assertThat(mapper.writeValueAsString(ApplicationStage.APPLIED)).isEqualTo("\"applied\"");
    assertThat(mapper.readValue("\"applied\"", ApplicationStage.class))
        .isEqualTo(ApplicationStage.APPLIED);

    var validation =
        ApiErrorResponse.withFieldErrors(
            400,
            "VALIDATION_ERROR",
            "Validation failed.",
            "/auth/login",
            List.of(new ApiErrorResponse.FieldError("email", "Email must be valid")));
    var fieldErrors = mapper.readTree(mapper.writeValueAsString(validation)).get("fieldErrors");
    assertThat(fieldErrors.get(0).get("field").asText()).isEqualTo("email");
    assertThat(
            mapper
                .readValue(
                    "{\"email\":\"user@example.test\",\"password\":\"Password1\",\"extra\":true}",
                    LoginRequest.class)
                .email())
        .isEqualTo("user@example.test");
  }
}
