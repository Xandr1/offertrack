package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ApplicationBoardQueryTest {
  @Test
  void normalizesSearchAndDefaultsOffset() {
    assertThat(ApplicationBoardQuery.initial("  acme  "))
        .isEqualTo(new ApplicationBoardQuery("acme", 0));
    assertThat(ApplicationBoardQuery.column("  ", null))
        .isEqualTo(new ApplicationBoardQuery(null, 0));
  }

  @Test
  void rejectsNegativeOffsetAndUnknownStage() {
    assertThatThrownBy(() -> ApplicationBoardQuery.column(null, -1))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> ApplicationBoardQuery.parseStage("unknown"))
        .isInstanceOf(ResponseStatusException.class);
  }
}
