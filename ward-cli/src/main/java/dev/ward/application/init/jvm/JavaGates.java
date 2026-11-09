package dev.ward.application.init.jvm;

import dev.ward.application.init.SourceLanguageGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;

/**
 * The gates of Java and Kotlin sources on Maven and Gradle: the shared JVM gates. The Ward rules of
 * the preset, selected in {@code ward-rules.toml}, replace Checkstyle, PMD and SpotBugs.
 */
final class JavaGates implements SourceLanguageGates<BuildToolGates> {
  private static final List<String> STANDARD = List.of("compile", "tests", "coverage");
  private static final List<String> STRICT =
      List.of("compile", "tests", "coverage", "enforcer", "mutation");

  @Override
  public List<InitGateOption> analysisGates(final BuildToolGates jvm) {
    return List.of(
        jvm.tests(),
        jvm.compile(),
        jvm.coverage(),
        jvm.sonar(),
        jvm.dependencyCheck(),
        jvm.mutation());
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    return preset == QualityPreset.STRICT ? STRICT : STANDARD;
  }
}
