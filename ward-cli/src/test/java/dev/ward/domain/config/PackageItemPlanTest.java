package dev.ward.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** A {@code {package}} item narrows a gate to the workspace packages that hold the edited paths. */
class PackageItemPlanTest {
  private static final Path UI = PartialPlans.ROOT.resolve("packages/ui");
  private static final Function<Path, Optional<String>> PACKAGES =
      path -> path.startsWith(UI) ? Optional.of("@repo/ui") : Optional.empty();

  @Test
  void twoFilesInOnePackageRenderTheItemOnce() {
    assertThat(
            plan("--filter=...{package}", PACKAGES, "packages/ui/src/a.ts", "packages/ui/src/b.ts"))
        .isEqualTo(PartialPlans.narrowed("--filter=...@repo/ui"));
  }

  @Test
  void rootFileInNoPackageRunsInFull() {
    assertThat(plan("--filter=...{package}", PACKAGES, "tsconfig.base.ts"))
        .isEqualTo(PartialPlans.full());
  }

  @Test
  void pathAndNamePlaceholdersAreRenderedAsBefore() {
    assertThat(plan("{path}", PACKAGES, "packages/ui/src/a.ts"))
        .isEqualTo(PartialPlans.narrowed("packages/ui/src/a.ts"));
  }

  private static PartialCheckPlan.GatePlan plan(
      final String item, final Function<Path, Optional<String>> packages, final String... paths) {
    return PartialCheckPlan.create(
            PartialPlans.ROOT,
            Map.of(
                PartialPlans.GATE,
                PartialPlans.gate(
                    PartialPlans.partial(
                        List.of("**/*.ts"), List.of(), List.of("{paths}"), item, null))),
            List.of(paths).stream().map(PartialPlans.ROOT::resolve).toList(),
            PartialPlans.EVERYTHING_EXISTS,
            packages)
        .gates()
        .get(PartialPlans.GATE);
  }
}
