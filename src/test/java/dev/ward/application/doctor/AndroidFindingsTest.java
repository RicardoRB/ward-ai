package dev.ward.application.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Doctor checks the Android SDK and the Android Gradle Plugin against the Gradle and JDK. */
class AndroidFindingsTest {
  private static final Project PROJECT =
      new Project(Path.of("/repo/android"), Language.JAVA, BuildTool.GRADLE, Framework.ANDROID);
  private static final String SDK = AndroidFindings.SDK;
  private static final String PLUGIN = AndroidFindings.PLUGIN;

  @Test
  void sdkFoundThroughAndroidHome() {
    assertThat(find(SDK, facts(Map.of("androidSdkHome", "true")), Optional.empty()))
        .isEqualTo(new Finding(SDK, Status.OK, "Android SDK found through ANDROID_HOME."));
  }

  @Test
  void sdkFoundThroughAndroidSdkRootWhenHomeIsMissing() {
    assertThat(find(SDK, facts(Map.of("androidSdkRoot", "true")), Optional.empty()))
        .isEqualTo(new Finding(SDK, Status.OK, "Android SDK found through ANDROID_SDK_ROOT."));
  }

  @Test
  void missingSdkWarnsNamingBothVariables() {
    final Finding sdk = find(SDK, facts(Map.of()), Optional.empty());

    assertThat(sdk.status()).isEqualTo(Status.WARNING);
    assertThat(sdk.message()).contains("ANDROID_HOME", "ANDROID_SDK_ROOT");
  }

  @Test
  void pluginNeedingNewerJdkWarnsWithTheJdkItNeeds() {
    final Finding plugin =
        find(PLUGIN, facts(Map.of("androidGradlePluginVersion", "8.7.3")), Optional.of(11));

    assertThat(plugin.status()).isEqualTo(Status.WARNING);
    assertThat(plugin.message()).contains("8.7.3", "JDK 17", "JDK 11");
  }

  @Test
  void pluginRunsOnCompatibleJdkAndGradleIsOk() {
    final Finding plugin =
        find(
            PLUGIN,
            facts(Map.of("androidGradlePluginVersion", "8.11.1", "gradleVersion", "8.13")),
            Optional.of(17));

    assertThat(plugin.status()).isEqualTo(Status.OK);
  }

  @Test
  void pluginNeedingNewerGradleWarnsWithTheGradleItNeeds() {
    final Finding plugin =
        find(
            PLUGIN,
            facts(Map.of("androidGradlePluginVersion", "8.11.1", "gradleVersion", "8.9")),
            Optional.of(17));

    assertThat(plugin.status()).isEqualTo(Status.WARNING);
    assertThat(plugin.message()).contains("Gradle 8.13", "Gradle 8.9");
  }

  @Test
  void pluginVersionComparisonUsesNumericParts() {
    assertThat(AndroidGradlePluginRequirements.older("8.9", "8.11.1")).isTrue();
    assertThat(AndroidGradlePluginRequirements.older("8.13", "8.11.1")).isFalse();
    assertThat(AndroidGradlePluginRequirements.older("8.11.1", "8.11.1")).isFalse();
  }

  /** The finding named {@code name}, from the SDK and plugin checks for these facts. */
  private static Finding find(
      final String name, final EnvironmentFacts facts, final Optional<Integer> jdk) {
    final ToolchainContext context =
        new ToolchainContext(Optional.of(fakeTools(jdk)), Optional.empty(), List.of());
    return AndroidFindings.findings(PROJECT, facts, context).stream()
        .filter(finding -> finding.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static EnvironmentFacts facts(final Map<String, String> values) {
    return new EnvironmentFacts(Set.of(), values);
  }

  /** Reports only the runtime JDK; the Android checks never ask for anything else. */
  private static BuildToolConfiguration fakeTools(final Optional<Integer> jdk) {
    return new BuildToolConfiguration() {
      @Override
      public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
        throw new UnsupportedOperationException();
      }

      @Override
      public PlannedFileChange plan(
          final Project project,
          final List<InitGateOption> gates,
          final QualityPreset preset,
          final boolean approved) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void rollback(final BuildFileChange change) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<Integer> runtimeJdk(final Project project) {
        return jdk;
      }
    };
  }
}
