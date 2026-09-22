package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;

import com.offertrack.applications.dto.CreateApplicationRequest;
import com.offertrack.applications.dto.ReplaceApplicationRequest;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationNotesValidationTest {
  @Test
  void bothDtosAllow20000CharactersAndReject20001() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      for (int length : new int[] {20000, 20001}) {
        String notes = "n".repeat(length);
        Object[] requests = {
          new CreateApplicationRequest(
              "Acme", "Engineer", null, null, null, null, notes, null, List.of()),
          new ReplaceApplicationRequest(
              "Acme", "Engineer", null, null, null, null, notes, null, List.of())
        };
        for (Object request : requests) {
          var violations = validator.validate(request);
          if (length == 20000) assertThat(violations).isEmpty();
          else
            assertThat(violations)
                .singleElement()
                .satisfies(
                    violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("notes"));
        }
      }
    }
  }
}
