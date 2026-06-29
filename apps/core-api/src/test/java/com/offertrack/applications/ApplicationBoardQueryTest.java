package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ApplicationBoardQueryTest {
  @Test
  void normalizesSearchAndDefaultsOffset() {
    assertThat(ApplicationBoardQuery.initial("  acme  "))
        .isEqualTo(
            new ApplicationBoardQuery(
                "acme",
                0,
                ApplicationListQuery.ApplicationSort.UPDATED_AT,
                ApplicationListQuery.SortDirection.DESC));
    assertThat(ApplicationBoardQuery.column("  ", null))
        .isEqualTo(
            new ApplicationBoardQuery(
                null,
                0,
                ApplicationListQuery.ApplicationSort.UPDATED_AT,
                ApplicationListQuery.SortDirection.DESC));
  }

  @Test
  void parsesSupportedSortAndDirection() {
    assertThat(ApplicationBoardQuery.initial(null, "createdAt", "asc"))
        .isEqualTo(
            new ApplicationBoardQuery(
                null,
                0,
                ApplicationListQuery.ApplicationSort.CREATED_AT,
                ApplicationListQuery.SortDirection.ASC));
  }

  @Test
  void rejectsNegativeOffsetAndUnknownStage() {
    assertThatThrownBy(() -> ApplicationBoardQuery.column(null, -1))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> ApplicationBoardQuery.parseStage("unknown"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rejectsUnsupportedSortAndDirection() {
    assertThatThrownBy(() -> ApplicationBoardQuery.initial(null, "companyName", null))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> ApplicationBoardQuery.initial(null, null, "sideways"))
        .isInstanceOf(ResponseStatusException.class);
  }
}
