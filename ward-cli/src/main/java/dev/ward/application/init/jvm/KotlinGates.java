package dev.ward.application.init.jvm;

import dev.ward.application.init.SourceLanguageGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;

/**
 * Kotlin's gates on Maven and Gradle. Kotlin keeps its external analysis tools until its own Ward
 * rules land; Java selects the Ward rules instead.
 */
final class KotlinGates implements SourceLanguageGates<BuildToolGates> {
  private static final List<String> STANDARD =
      List.of("compile", "tests", "checkstyle", "pmd", "spotbugs", "coverage");
  private static final List<String> STRICT =
      List.of(
          "compile", "tests", "checkstyle", "pmd", "spotbugs", "coverage", "enforcer", "mutation");

  @Override
  public List<InitGateOption> analysisGates(final BuildToolGates jvm) {
    return List.of(
        jvm.tests(),
        jvm.compile(),
        jvm.coverage(),
        jvm.tool(
            "spotbugs",
            "Finds potential bugs through static analysis.",
            "spotbugsMain",
            "spotbugs:check"),
        jvm.tool(
            "checkstyle",
            "Checks source style and formatting rules.",
            "checkstyleMain",
            "checkstyle:check"),
        jvm.tool(
            "pmd",
            "Finds common Java design and implementation problems with PMD.",
            "pmdMain",
            "pmd:check"),
        jvm.sonar(),
        jvm.dependencyCheck(),
        jvm.mutation());
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    return preset == QualityPreset.STRICT ? STRICT : STANDARD;
  }
}
