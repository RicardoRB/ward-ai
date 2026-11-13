package dev.ward.infrastructure.init.maven;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.buildpolicy.BuildModel;
import dev.ward.domain.buildpolicy.BuildPolicyInput;
import dev.ward.domain.buildpolicy.BuildPolicyRules;
import dev.ward.domain.buildpolicy.BuildRule;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.buildpolicy.maven.MavenBuildReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The strict pins satisfy the native plugin version rule. */
class PomLifecyclePinBuildPolicyTest {
  private static final String POM =
      "<project>\n  <modelVersion>4.0.0</modelVersion>\n  <groupId>a</groupId>\n"
          + "  <artifactId>b</artifactId>\n  <version>1</version>\n</project>\n";

  @TempDir Path root;

  @Test
  void strictPinsWithOnlyTheBuildPolicyGateAndLeavesNoPluginFinding() throws IOException {
    Files.writeString(root.resolve("pom.xml"), POM);
    new PomToolConfigurator()
        .apply(
            project(),
            List.of(new InitGateOption("build-policy", "build-policy", List.of(), true, "ok")),
            QualityPreset.STRICT);

    final BuildModel model = new MavenBuildReader().read(root, "");
    final BuildPolicyInput input = new BuildPolicyInput(model, () -> null, Map.of());
    assertThat(BuildPolicyRules.forRule(BuildRule.PLUGIN_VERSION_PINNED).check(input)).isEmpty();
  }

  @Test
  void aPlainStrictPomWithoutPinsHasOneFindingPerLifecyclePlugin() throws IOException {
    Files.writeString(root.resolve("pom.xml"), POM);

    final BuildModel model = new MavenBuildReader().read(root, "");
    final BuildPolicyInput input = new BuildPolicyInput(model, () -> null, Map.of());
    assertThat(BuildPolicyRules.forRule(BuildRule.PLUGIN_VERSION_PINNED).check(input))
        .hasSameSizeAs(PresetPluginSnippets.LIFECYCLE_PLUGINS);
  }

  private Project project() {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }
}
