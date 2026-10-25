package dev.ward.domain.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompatibilityResolverTest {
  static final int JAVA_8 = 8;
  static final int JAVA_17 = 17;
  static final int NEWEST_JAVA = 25;
  static final String ARTIFACT = "demo-plugin";
  static final String MAVEN = "3.9.9";
  static final Project PROJECT =
      new Project(Path.of("."), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  static final CompatibilityResolver RESOLVER = new CompatibilityResolver();

  @Test
  void selectsHighestCompatibleVersionForMissingPlugin() {
    final CompatibilityPlan plan =
        RESOLVER.resolve(
            snapshot(JAVA_17, Optional.of("3.8.8"), Optional.empty(), Map.of()),
            Map.of(
                ARTIFACT,
                List.of(
                    candidate(ARTIFACT, "1.0.0", JAVA_8, "3.6"),
                    candidate(ARTIFACT, "2.0.0", 11, "3.8"))),
            List.of(ARTIFACT));

    assertThat(plan.findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.selectedVersion()).isEqualTo("2.0.0");
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.COMPATIBLE);
            });
  }

  @Test
  void recommendsUpgradeForIncompatibleExistingPlugin() {
    final CompatibilityPlan plan =
        RESOLVER.resolve(
            snapshot(JAVA_17, Optional.of("3.8.8"), Optional.empty(), Map.of(ARTIFACT, "0.1.0")),
            Map.of(ARTIFACT, List.of(candidate(ARTIFACT, "2.0.0", 11, "3.8"))),
            List.of(ARTIFACT));

    assertThat(plan.changes())
        .singleElement()
        .extracting(CompatibilityFinding::selectedVersion)
        .isEqualTo("2.0.0");
    assertThat(plan.findings())
        .singleElement()
        .extracting(CompatibilityFinding::outcome)
        .isEqualTo(CompatibilityOutcome.UPGRADE_RECOMMENDED);
  }

  @Test
  void reportsUnknownWhenBuildToolVersionIsUnavailable() {
    final CompatibilityPlan plan =
        RESOLVER.resolve(
            snapshot(JAVA_17, Optional.empty(), Optional.empty(), Map.of()),
            Map.of(ARTIFACT, List.of(candidate(ARTIFACT, "1.0.0", JAVA_8, "3.6"))),
            List.of(ARTIFACT));

    assertThat(plan.hasUnresolvableFindings()).isTrue();
    assertThat(plan.findings())
        .singleElement()
        .extracting(CompatibilityFinding::outcome)
        .isEqualTo(CompatibilityOutcome.UNKNOWN);
  }

  @Test
  void catalogSelectsJava25CapablePitForMissingAndStalePlugins() {
    final String pit = "pitest-maven";
    final Map<String, List<ManagedPluginCandidate>> catalog =
        Map.of(pit, ManagedPluginCatalog.entries().get(pit));

    final CompatibilityPlan missing =
        RESOLVER.resolve(
            snapshot(NEWEST_JAVA, Optional.of(MAVEN), Optional.empty(), Map.of()),
            catalog,
            List.of(pit));
    final CompatibilityPlan stale =
        RESOLVER.resolve(
            snapshot(NEWEST_JAVA, Optional.of(MAVEN), Optional.empty(), Map.of(pit, "1.17.4")),
            catalog,
            List.of(pit));

    assertThat(missing.findings())
        .singleElement()
        .extracting(CompatibilityFinding::selectedVersion)
        .isEqualTo("1.30.1");
    assertThat(stale.findings())
        .singleElement()
        .extracting(CompatibilityFinding::selectedVersion)
        .isEqualTo("1.30.1");
  }

  static CompatibilityProjectSnapshot snapshot(
      final int java,
      final Optional<String> buildTool,
      final Optional<Integer> release,
      final Map<String, String> plugins) {
    return new CompatibilityProjectSnapshot(
        PROJECT,
        new ToolchainSnapshot(Optional.of(java), release, buildTool, Framework.NONE),
        plugins);
  }

  static ManagedPluginCandidate candidate(
      final String artifact,
      final String version,
      final int minimumJava,
      final String minimumBuildTool) {
    return new ManagedPluginCandidate(
        artifact, version, minimumJava, NEWEST_JAVA, minimumBuildTool, "plugin");
  }
}
