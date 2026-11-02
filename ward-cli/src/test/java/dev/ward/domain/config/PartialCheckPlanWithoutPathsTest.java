package dev.ward.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** An {@code edit} run without edited paths skips checks with a paths table and runs the rest. */
class PartialCheckPlanWithoutPathsTest {

  @Test
  void checksWithPathsAreSkippedAndOthersRunInFull() {
    final GateConfiguration narrowable =
        new GateConfiguration(
            "checkstyle",
            "checkstyle",
            true,
            List.of("./mvnw", "checkstyle:check"),
            Set.of("edit"),
            new PartialGateConfiguration(
                List.of("src/**/*.java"), List.of(), List.of("{paths}"), null, null),
            null,
            null,
            null,
            null);
    final GateConfiguration plain = new GateConfiguration("tests", true, List.of("./mvnw", "test"));

    final PartialCheckPlan plan =
        PartialCheckPlan.withoutPaths(Map.of("checkstyle", narrowable, "tests", plain));

    assertThat(plan.gates().get("checkstyle").scope()).isEqualTo(PartialCheckPlan.Scope.SKIP);
    assertThat(plan.gates().get("tests").scope()).isEqualTo(PartialCheckPlan.Scope.FULL);
    assertThat(plan.gates().get("tests").args()).isEmpty();
  }
}
