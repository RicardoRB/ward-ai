package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.WorkspaceMember;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Root gates of a Turborepo workspace: task gates run turbo, repository-wide gates stay. */
class TurborepoGatePlanTest {
  private static final PackageManifest TYPESCRIPT =
      new PackageManifest(Map.of("typescript", "5.9.3"), null, null);

  @TempDir Path root;

  @Test
  void rootTaskGatesRunTurboThroughThePnpmRunner() {
    final Map<String, InitGateOption> gates = rootGates(BuildTool.PNPM);

    assertThat(gates.get("tsc").command())
        .containsExactly(
            "pnpm",
            "exec",
            "turbo",
            "run",
            "check-types",
            "--log-prefix=task",
            "--log-order=grouped",
            "--continue=always",
            "--ui=stream");
    assertThat(gates.get("eslint").command())
        .containsSubsequence("pnpm", "exec", "turbo", "run", "lint");
  }

  @Test
  void npmRootUsesNpx() {
    assertThat(rootGates(BuildTool.NPM).get("tsc").command())
        .startsWith("npx", "turbo", "run", "check-types");
  }

  @Test
  void repositoryWideGatesStayAsSingleRunsFromTheRoot() {
    assertThat(rootGates(BuildTool.PNPM).get("dependency-cruiser").command())
        .doesNotContain("turbo");
  }

  @Test
  void testGateFollowsTheMajorityRunnerAndTiesGoToVitest() {
    final List<WorkspaceMember> members =
        List.of(
            TurborepoFixtures.memberOf(root, TurborepoFixtures.WEB, Framework.NONE),
            TurborepoFixtures.memberOf(root, TurborepoFixtures.API, Framework.NONE),
            TurborepoFixtures.memberOf(root, "apps/jobs", Framework.NONE));
    final Map<Path, PackageManifest> jest =
        Map.of(
            root.resolve(TurborepoFixtures.WEB),
                new PackageManifest(Map.of("jest", "29.0.0"), null, null),
            root.resolve(TurborepoFixtures.API),
                new PackageManifest(Map.of("jest", "29.0.0"), null, null));

    assertThat(
            TurborepoPlan.testGate(
                TurborepoFixtures.rootProject(root, BuildTool.PNPM),
                TurborepoFixtures.inputs(root, members, jest)))
        .isEqualTo("jest");
    assertThat(
            TurborepoPlan.testGate(
                TurborepoFixtures.rootProject(root, BuildTool.PNPM),
                TurborepoFixtures.inputs(
                    root,
                    List.of(
                        TurborepoFixtures.memberOf(root, TurborepoFixtures.WEB, Framework.NONE),
                        TurborepoFixtures.memberOf(root, TurborepoFixtures.API, Framework.NONE)),
                    Map.of(
                        root.resolve(TurborepoFixtures.WEB),
                        new PackageManifest(Map.of("jest", "29.0.0"), null, null),
                        root.resolve(TurborepoFixtures.API),
                        TurborepoFixtures.EMPTY))))
        .isEqualTo("vitest");
  }

  private Map<String, InitGateOption> rootGates(final BuildTool manager) {
    final WorkspaceMember web =
        TurborepoFixtures.memberOf(root, TurborepoFixtures.WEB, Framework.NONE);
    final Map<String, InitGateOption> gates = new LinkedHashMap<>();
    new NodeInitSupport()
        .gates(
            TurborepoFixtures.rootProject(root, manager),
            QualityPreset.STANDARD,
            TurborepoFixtures.inputs(
                root, List.of(web), Map.of(web.directory(), TYPESCRIPT), Set.of(web.directory())))
        .analysis()
        .forEach(gate -> gates.put(gate.id(), gate));
    return gates;
  }
}
