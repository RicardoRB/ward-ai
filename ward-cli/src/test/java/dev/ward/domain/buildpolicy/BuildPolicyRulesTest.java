package dev.ward.domain.buildpolicy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BuildPolicyRulesTest {
  @Test
  void anUndeterminableGradlePluginVersionIsAFinding() {
    final List<BuildPolicyFinding> findings =
        RuleModels.run(
            BuildRule.PLUGIN_VERSION_PINNED,
            RuleModels.declarations(
                RuleModels.declaration(
                    BuildEcosystem.GRADLE,
                    DeclarationKind.PLUGIN,
                    null,
                    Declaration.Flag.UNDETERMINABLE)));
    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.message()).contains("cannot be determined"));
  }

  @Test
  void aMavenPluginWithoutVersionUnderAnExternalParentSaysSo() {
    final List<BuildPolicyFinding> findings =
        RuleModels.run(
            BuildRule.PLUGIN_VERSION_PINNED,
            RuleModels.declarations(
                RuleModels.declaration(
                    BuildEcosystem.MAVEN,
                    DeclarationKind.PLUGIN,
                    null,
                    Declaration.Flag.EXTERNAL_PARENT)));
    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.message()).contains("external parent"));
  }

  @Test
  void anUnresolvedPropertyIsNotAnExactPluginVersion() {
    assertThat(
            RuleModels.run(
                BuildRule.PLUGIN_VERSION_PINNED,
                RuleModels.declarations(
                    RuleModels.declaration(
                        BuildEcosystem.MAVEN,
                        DeclarationKind.PLUGIN,
                        "${missing}",
                        Declaration.Flag.NONE))))
        .hasSize(1);
  }

  @Test
  void mavenVersionsAreDynamicEvenWithoutALockfile() {
    for (final String version : List.of("[1,2)", "LATEST", "RELEASE", "1.0-SNAPSHOT")) {
      assertThat(
              RuleModels.run(
                  BuildRule.NO_DYNAMIC_VERSION,
                  RuleModels.declarations(
                      RuleModels.declaration(
                          BuildEcosystem.MAVEN,
                          DeclarationKind.DEPENDENCY,
                          version,
                          Declaration.Flag.LOCKED))))
          .as(version)
          .hasSize(1);
    }
  }

  @Test
  void aLockfilePinsRangesButNotTagsOrWildcards() {
    final BuildModel ranges =
        RuleModels.declarations(
            RuleModels.declaration(
                BuildEcosystem.NODE, DeclarationKind.DEPENDENCY, "^1.3.0", Declaration.Flag.LOCKED),
            RuleModels.declaration(
                BuildEcosystem.PYTHON, DeclarationKind.DEPENDENCY, ">=2", Declaration.Flag.LOCKED));
    final BuildModel fixed =
        RuleModels.declarations(
            RuleModels.declaration(
                BuildEcosystem.NODE, DeclarationKind.DEPENDENCY, "latest", Declaration.Flag.LOCKED),
            RuleModels.declaration(
                BuildEcosystem.NODE, DeclarationKind.DEPENDENCY, "*", Declaration.Flag.LOCKED),
            RuleModels.declaration(
                BuildEcosystem.GRADLE, DeclarationKind.PLUGIN, "1.+", Declaration.Flag.LOCKED));
    assertThat(RuleModels.run(BuildRule.NO_DYNAMIC_VERSION, ranges)).isEmpty();
    assertThat(RuleModels.run(BuildRule.NO_DYNAMIC_VERSION, fixed))
        .hasSize(fixed.declarations().size());
  }

  @Test
  void nodeRangesWithoutALockfileAndOpenEndedRangesAreDynamic() {
    assertThat(
            RuleModels.run(
                BuildRule.NO_DYNAMIC_VERSION,
                RuleModels.declarations(
                    RuleModels.declaration(
                        BuildEcosystem.NODE,
                        DeclarationKind.DEPENDENCY,
                        "^1.3.0",
                        Declaration.Flag.NONE),
                    RuleModels.declaration(
                        BuildEcosystem.NODE,
                        DeclarationKind.DEPENDENCY,
                        ">=1.0.0",
                        Declaration.Flag.LOCKED),
                    RuleModels.declaration(
                        BuildEcosystem.NODE,
                        DeclarationKind.DEPENDENCY,
                        "1.2.3",
                        Declaration.Flag.NONE),
                    RuleModels.declaration(
                        BuildEcosystem.NODE,
                        DeclarationKind.DEPENDENCY,
                        "git+https://x/y.git",
                        Declaration.Flag.NONE))))
        .hasSize(2);
  }

  @Test
  void pythonExactPinsAreFine() {
    assertThat(
            RuleModels.run(
                BuildRule.NO_DYNAMIC_VERSION,
                RuleModels.declarations(
                    RuleModels.declaration(
                        BuildEcosystem.PYTHON,
                        DeclarationKind.DEPENDENCY,
                        "==2.32.0",
                        Declaration.Flag.NONE),
                    RuleModels.declaration(
                        BuildEcosystem.PYTHON,
                        DeclarationKind.DEPENDENCY,
                        "*",
                        Declaration.Flag.NONE))))
        .hasSize(1);
  }
}
