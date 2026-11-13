package dev.ward.domain.buildpolicy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyRelation;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.dependency.RequestedVersion;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildPolicyToolAndConvergenceTest {
  @Test
  void everyRuleHasAnImplementation() {
    for (final BuildRule rule : BuildRule.values()) {
      assertThat(BuildPolicyRules.forRule(rule)).isNotNull();
      assertThat(rule.id()).isEqualTo("ward:build/" + rule.ruleName());
    }
  }

  @Test
  void minimumBuildToolVersionDefaultsMavenOnly() {
    final BuildModel model =
        new BuildModel(
            List.of(),
            List.of(
                new ToolVersion("maven", "3.8.8", "w", 1),
                new ToolVersion("maven", "3.9.9", "w", 1),
                new ToolVersion("gradle", "1.0", "g", 1)));
    assertThat(RuleModels.run(BuildRule.MINIMUM_BUILD_TOOL_VERSION, model))
        .singleElement()
        .satisfies(f -> assertThat(f.message()).contains("3.8.8").contains("3.9"));
    final List<BuildPolicyFinding> custom =
        BuildPolicyRules.forRule(BuildRule.MINIMUM_BUILD_TOOL_VERSION)
            .check(new BuildPolicyInput(model, () -> null, Map.of("gradle", "8.5")));
    assertThat(custom).hasSize(2);
  }

  @Test
  void versionsCompareNumerically() {
    assertThat(Versions.compare("3.10", "3.9")).isPositive();
    assertThat(Versions.compare("3.9", "3.9.0")).isZero();
    assertThat(Versions.compare("3.8.8", "3.9")).isNegative();
  }

  @Test
  void convergenceReportsPackagesWithSeveralVersionsInOneModule() {
    final DependencyInventory inventory =
        new DependencyInventory(
            List.of(
                new ModuleDependencies(
                    "",
                    List.of(
                        RuleModels.entry("semver", "6.3.1"),
                        RuleModels.entry("semver", "7.6.0"),
                        RuleModels.entry("left-pad", "1.3.0")))));
    final List<BuildPolicyFinding> findings =
        BuildPolicyRules.forRule(BuildRule.DEPENDENCY_CONVERGENCE)
            .check(new BuildPolicyInput(BuildModel.EMPTY, () -> inventory, Map.of()));
    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> assertThat(f.message()).contains("semver").contains("6.3.1").contains("7.6.0"));
  }

  @Test
  void convergenceCountsTheVersionsRequestedBeforeMediation() {
    final BuildPolicyFinding finding = mavenRun(BuildRule.DEPENDENCY_CONVERGENCE, "3.0").getFirst();
    assertThat(finding.message()).contains("g:lib").contains("3.0").contains("3.12.0");
    assertThat(mavenRun(BuildRule.DEPENDENCY_CONVERGENCE, "3.12.0")).isEmpty();
  }

  @Test
  void upperBoundReportsAResolvedVersionBelowARequestedOne() {
    assertThat(mavenRun(BuildRule.REQUIRE_UPPER_BOUND, "3.0"))
        .singleElement()
        .satisfies(
            f ->
                assertThat(f.message())
                    .contains("g:lib resolves to 3.0")
                    .contains("3.12.0 (pkg:maven/g/text@1.10.0)"));
  }

  @Test
  void upperBoundAcceptsAHigherResolvedVersionAndMavenOrder() {
    assertThat(mavenRun(BuildRule.REQUIRE_UPPER_BOUND, "3.14.0")).isEmpty();
    assertThat(mavenRun(BuildRule.REQUIRE_UPPER_BOUND, "3.12.0")).isEmpty();
    assertThat(mavenRun(BuildRule.REQUIRE_UPPER_BOUND, "3.9")).hasSize(1);
    assertThat(mavenRun(BuildRule.REQUIRE_UPPER_BOUND, "3.12.0-rc1")).hasSize(1);
  }

  @Test
  void upperBoundIgnoresSourcesThatReportNoRequestsAndOtherEcosystems() {
    final DependencyInventory inventory =
        new DependencyInventory(
            List.of(new ModuleDependencies("", List.of(RuleModels.entry("semver", "6.3.1")))));
    assertThat(
            BuildPolicyRules.forRule(BuildRule.REQUIRE_UPPER_BOUND)
                .check(new BuildPolicyInput(BuildModel.EMPTY, () -> inventory, Map.of())))
        .isEmpty();
  }

  @Test
  void theInventoryRulesAreNotSelectedByTheDefaults() {
    assertThat(BuildRule.DEPENDENCY_CONVERGENCE.strict()).isFalse();
    assertThat(BuildRule.REQUIRE_UPPER_BOUND.strict()).isFalse();
  }

  /** A module that requests g:lib at {@code resolved} while g:text 1.10.0 asks for 3.12.0. */
  private static List<BuildPolicyFinding> mavenRun(final BuildRule rule, final String resolved) {
    final PackageId lib = new PackageId(Ecosystem.MAVEN, "g:lib", resolved);
    final PackageId text = new PackageId(Ecosystem.MAVEN, "g:text", "1.10.0");
    final Dependency libEntry =
        new Dependency(
            lib,
            DependencyRelation.DIRECT,
            DependencyScope.RUNTIME,
            true,
            "pom.xml",
            "pom.xml",
            Set.of(),
            Set.of(RequestedVersion.byModule(resolved), RequestedVersion.by(text, "3.12.0")));
    final Dependency textEntry =
        new Dependency(
            text,
            DependencyRelation.DIRECT,
            DependencyScope.RUNTIME,
            true,
            "pom.xml",
            "pom.xml",
            Set.of(),
            Set.of(RequestedVersion.byModule("1.10.0")));
    final DependencyInventory inventory =
        new DependencyInventory(List.of(new ModuleDependencies("", List.of(libEntry, textEntry))));
    return BuildPolicyRules.forRule(rule)
        .check(new BuildPolicyInput(BuildModel.EMPTY, () -> inventory, Map.of()));
  }
}
