package com.offertrack.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CoreRuntimeModeTest {
  @Test
  void omittedModeResolvesToServer() {
    assertThat(CoreRuntimeMode.resolve(Map.of())).isEqualTo(CoreRuntimeMode.SERVER);
  }

  @Test
  void exactServerAndMigrateValuesResolve() {
    assertThat(CoreRuntimeMode.resolve(Map.of("OFFERTRACK_RUN_MODE", "server")))
        .isEqualTo(CoreRuntimeMode.SERVER);
    assertThat(CoreRuntimeMode.resolve(Map.of("OFFERTRACK_RUN_MODE", "migrate")))
        .isEqualTo(CoreRuntimeMode.MIGRATE);
  }

  @Test
  void blankInexactAndUnknownValuesFail() {
    for (String value : new String[] {"", " ", "SERVER", "migration", "unknown"}) {
      assertThatThrownBy(() -> CoreRuntimeMode.resolve(Map.of("OFFERTRACK_RUN_MODE", value)))
          .isInstanceOf(CoreStartupException.class)
          .hasMessageContaining("INVALID_RUNTIME_MODE");
    }
  }

  @Test
  void serverModeLaunchesTheExistingApplicationPath() {
    AtomicInteger launches = new AtomicInteger();
    CoreProcessDispatcher dispatcher =
        new CoreProcessDispatcher(
            arguments -> launches.incrementAndGet(), new FlywayMigrationRunner());

    CoreProcessResult result =
        dispatcher.dispatch(Map.of("OFFERTRACK_RUN_MODE", "server"), new String[] {"--example"});

    assertThat(result.kind()).isEqualTo(CoreProcessResult.Kind.SERVER_STARTED);
    assertThat(launches).hasValue(1);
  }

  @Test
  void migrateModeNeverLaunchesServerAndValidatesBeforeConnecting() {
    AtomicInteger launches = new AtomicInteger();
    CoreProcessDispatcher dispatcher =
        new CoreProcessDispatcher(
            arguments -> launches.incrementAndGet(), new FlywayMigrationRunner());

    assertThatThrownBy(
            () ->
                dispatcher.dispatch(
                    Map.of(
                        "OFFERTRACK_RUN_MODE",
                        "migrate",
                        "DATABASE_URL",
                        "jdbc:postgresql://127.0.0.1:1/offertrack",
                        "DB_USER",
                        "user"),
                    new String[0]))
        .isInstanceOf(CoreStartupException.class)
        .hasMessageContaining("DB_PASSWORD");
    assertThat(launches).hasValue(0);
  }
}
