package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks an Android project's SDK and the Android Gradle Plugin version against the Gradle and JDK
 * that run it. Both are warnings: a CI runner can install the SDK after doctor runs.
 */
final class AndroidFindings {
  static final String SDK = "android-sdk";
  static final String PLUGIN = "android-gradle-plugin";
  private static final String HOME = "androidSdkHome";
  private static final String ROOT = "androidSdkRoot";
  private static final String PLUGIN_VERSION = "androidGradlePluginVersion";
  private static final String GRADLE_VERSION = "gradleVersion";

  private AndroidFindings() {}

  /** Returns the SDK finding and the plugin finding for {@code detected}. */
  static List<Finding> findings(
      final Project detected, final EnvironmentFacts facts, final ToolchainContext context) {
    final List<Finding> findings = new ArrayList<>();
    findings.add(sdk(facts));
    final String plugin = facts.values().getOrDefault(PLUGIN_VERSION, "");
    if (!plugin.isEmpty()) {
      findings.add(pluginFinding(plugin, facts, jdk(detected, context)));
    }
    return findings;
  }

  private static Finding sdk(final EnvironmentFacts facts) {
    if (flag(facts, HOME)) {
      return new Finding(SDK, Status.OK, "Android SDK found through ANDROID_HOME.");
    }
    if (flag(facts, ROOT)) {
      return new Finding(SDK, Status.OK, "Android SDK found through ANDROID_SDK_ROOT.");
    }
    return new Finding(
        SDK,
        Status.WARNING,
        "No Android SDK found: set ANDROID_HOME or ANDROID_SDK_ROOT to an existing directory.");
  }

  /** Names the JDK or Gradle the plugin needs when the build's Gradle or JDK is older. */
  private static Finding pluginFinding(
      final String plugin, final EnvironmentFacts facts, final Optional<Integer> jdk) {
    final Optional<Integer> needJdk = AndroidGradlePluginRequirements.minimumJdk(plugin);
    if (needJdk.isPresent() && jdk.isPresent() && jdk.get() < needJdk.get()) {
      return new Finding(
          PLUGIN,
          Status.WARNING,
          "Android Gradle Plugin "
              + plugin
              + " needs JDK "
              + needJdk.get()
              + " or newer, and the build runs on JDK "
              + jdk.get()
              + ".");
    }
    final String gradle = facts.values().getOrDefault(GRADLE_VERSION, "");
    final Optional<String> needGradle = AndroidGradlePluginRequirements.minimumGradle(plugin);
    if (needGradle.isPresent()
        && !gradle.isEmpty()
        && AndroidGradlePluginRequirements.older(gradle, needGradle.get())) {
      return new Finding(
          PLUGIN,
          Status.WARNING,
          "Android Gradle Plugin "
              + plugin
              + " needs Gradle "
              + needGradle.get()
              + " or newer, and the wrapper pins Gradle "
              + gradle
              + ".");
    }
    return new Finding(
        PLUGIN, Status.OK, "Android Gradle Plugin " + plugin + " runs on this Gradle and JDK.");
  }

  private static Optional<Integer> jdk(final Project detected, final ToolchainContext context) {
    return context.buildTools().flatMap(tools -> tools.runtimeJdk(detected));
  }

  private static boolean flag(final EnvironmentFacts facts, final String name) {
    return Boolean.parseBoolean(facts.values().get(name));
  }
}
