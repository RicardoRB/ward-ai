package dev.ward.domain.init;

import static dev.ward.domain.init.CompatibilityResolverTest.candidate;
import static dev.ward.domain.init.CompatibilityResolverTest.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Framework;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Resolution by the runtime JDK, the release, and the build tool version. */
class CompatibilityResolverJdkTest {
  private static final int JAVA_8 = 8;
  private static final int JAVA_17 = 17;
  private static final int JAVA_21 = 21;
  private static final int JAVA_27 = 27;
  private static final int NEWEST_JAVA = 25;
  private static final String ARTIFACT = "demo-plugin";
  private static final String MAVEN = "3.9.9";
  private static final CompatibilityResolver RESOLVER = new CompatibilityResolver();
  private static final String SPOTBUGS = "spotbugs-maven-plugin";

  @Test
  void reportsUnknownWhenRuntimeJdkIsUnavailable() {
    final CompatibilityPlan plan =
        RESOLVER.resolve(
            new CompatibilityProjectSnapshot(
                CompatibilityResolverTest.PROJECT,
                new ToolchainSnapshot(
                    Optional.empty(), Optional.of(JAVA_17), Optional.of(MAVEN), Framework.NONE),
                Map.of()),
            Map.of(ARTIFACT, List.of(candidate(ARTIFACT, "1.0.0", JAVA_8, "3.6"))),
            List.of(ARTIFACT));

    assertThat(plan.findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.UNKNOWN);
              assertThat(finding.message()).contains("Runtime JDK could not be determined");
            });
  }

  @Test
  void reportsUnknownWhenReleaseIsAboveRuntimeJdk() {
    final CompatibilityPlan plan =
        RESOLVER.resolve(
            snapshot(JAVA_17, Optional.of(MAVEN), Optional.of(JAVA_21), Map.of()),
            Map.of(ARTIFACT, List.of(candidate(ARTIFACT, "1.0.0", JAVA_8, "3.6"))),
            List.of(ARTIFACT));

    assertThat(plan.findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.UNKNOWN);
              assertThat(finding.message()).contains("release 21", "runtime JDK 17");
            });
  }

  @Test
  void java8ProjectOnJdk8GetsTheJdk8SpotBugs() {
    final CompatibilityPlan plan =
        RESOLVER.resolve(
            snapshot(JAVA_8, Optional.of(MAVEN), Optional.of(JAVA_8), Map.of()),
            ManagedPluginCatalog.entries(),
            List.of(SPOTBUGS));

    assertThat(plan.findings())
        .singleElement()
        .extracting(CompatibilityFinding::selectedVersion)
        .isEqualTo("4" + ".8.6.6");
  }

  @Test
  void release8BuiltOnJdk21UsesTheJdk21Versions() {
    final CompatibilityPlan plan =
        RESOLVER.resolve(
            snapshot(JAVA_21, Optional.of(MAVEN), Optional.of(JAVA_8), Map.of()),
            ManagedPluginCatalog.entries(),
            List.of(SPOTBUGS, "spotless-maven-plugin", "google-java-format"));

    assertThat(plan.findings())
        .extracting(CompatibilityFinding::selectedVersion)
        .containsExactly("4" + ".9.7.0", "3.10.3", "1.36.1");
  }

  @Test
  void jdkAbove25SelectsTheJdk25Versions() {
    final CompatibilityPlan plan =
        RESOLVER.resolve(
            snapshot(JAVA_27, Optional.of(MAVEN), Optional.empty(), Map.of()),
            ManagedPluginCatalog.entries(),
            List.of(SPOTBUGS, "spotless-maven-plugin", "google-java-format"));

    assertThat(plan.findings())
        .extracting(CompatibilityFinding::selectedVersion)
        .containsExactly("4" + ".9.7.0", "3.10.3", "1.36.1");
  }

  @Test
  void gapIsReportedAsAnOmissionNamingTheLowestJdk() {
    final CompatibilityPlan plan =
        RESOLVER.resolve(
            snapshot(JAVA_8, Optional.of(MAVEN), Optional.empty(), Map.of()),
            Map.of(ARTIFACT, List.of(candidate(ARTIFACT, "2.0.0", 11, "3.6"))),
            List.of(ARTIFACT));

    assertThat(plan.hasUnresolvableFindings()).isFalse();
    assertThat(plan.findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.UNAVAILABLE_ON_JDK);
              assertThat(finding.selectedVersion()).isNull();
              assertThat(finding.message())
                  .isEqualTo(ARTIFACT + " needs JDK 11+; this project builds on JDK 8");
            });
  }

  @Test
  void gradleVersionTooOldSelectsAnOlderVersionOrOmits() {
    final Map<String, List<ManagedPluginCandidate>> catalog =
        Map.of(
            ARTIFACT,
            List.of(
                candidate(ARTIFACT, "1.0.0", JAVA_8, "7.0"),
                new ManagedPluginCandidate(ARTIFACT, "2.0.0", JAVA_8, NEWEST_JAVA, "8.4", "new")));

    final CompatibilityPlan older =
        RESOLVER.resolve(
            snapshot(JAVA_17, Optional.of("8.0"), Optional.empty(), Map.of()),
            catalog,
            List.of(ARTIFACT));
    final CompatibilityPlan omitted =
        RESOLVER.resolve(
            snapshot(JAVA_17, Optional.of("6.9"), Optional.empty(), Map.of()),
            catalog,
            List.of(ARTIFACT));

    assertThat(older.findings())
        .singleElement()
        .extracting(CompatibilityFinding::selectedVersion)
        .isEqualTo("1.0.0");
    assertThat(omitted.findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.UNAVAILABLE_ON_JDK);
              assertThat(finding.message()).contains("needs build tool version 7.0+", "6.9");
            });
  }
}
