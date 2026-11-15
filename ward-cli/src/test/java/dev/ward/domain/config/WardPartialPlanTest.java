package dev.ward.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.SupportedQualityGates;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which {@code ward} analysis checks a partial check skips, narrows to paths, or runs in full. */
class WardPartialPlanTest {
  private static final String WARD = SupportedQualityGates.ANALYSIS;
  private static final PartialGateConfiguration WARD_PARTIAL =
      new PartialGateConfiguration(
          List.of("src/**/*.java"), List.of("config/ward.rules"), List.of(), "{path}", ",");

  @Test
  void wardSourceEditNarrowsToTheChangedPathWithoutArguments() {
    assertThat(wardPlan(WARD_PARTIAL, "src/main/java/com/acme/Order.java"))
        .isEqualTo(
            new PartialCheckPlan.GatePlan(
                WARD,
                PartialCheckPlan.Scope.NARROW,
                List.of(),
                List.of("src/main/java/com/acme/Order.java")));
  }

  @Test
  void wardIrrelevantEditSkipsTheGate() {
    assertThat(wardPlan(WARD_PARTIAL, "README.md"))
        .isEqualTo(new PartialCheckPlan.GatePlan(WARD, PartialCheckPlan.Scope.SKIP, List.of()));
  }

  @Test
  void wardFullPatternRunsTheGateInFull() {
    assertThat(wardPlan(WARD_PARTIAL, "src/main/java/com/acme/Order.java", "config/ward.rules"))
        .isEqualTo(new PartialCheckPlan.GatePlan(WARD, PartialCheckPlan.Scope.FULL, List.of()));
  }

  @Test
  void wardGateWithoutPathsRunsInFull() {
    assertThat(wardPlan(WARD_PARTIAL))
        .isEqualTo(new PartialCheckPlan.GatePlan(WARD, PartialCheckPlan.Scope.FULL, List.of()));
  }

  /** Plans one {@code ward} check for the paths relative to the project root. */
  private static PartialCheckPlan.GatePlan wardPlan(
      final PartialGateConfiguration partial, final String... paths) {
    final GateConfiguration gate =
        new GateConfiguration(
            WARD,
            WARD,
            true,
            List.of(),
            Set.of("edit"),
            partial,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    return PartialCheckPlan.create(
            PartialPlans.ROOT,
            Map.of(WARD, gate),
            List.of(paths).stream().map(PartialPlans.ROOT::resolve).toList(),
            PartialPlans.EVERYTHING_EXISTS,
            path -> Optional.empty())
        .gates()
        .get(WARD);
  }
}
