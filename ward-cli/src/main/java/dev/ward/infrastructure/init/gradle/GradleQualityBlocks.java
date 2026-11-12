package dev.ward.infrastructure.init.gradle;

import dev.ward.domain.init.QualityPreset;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gradle extension blocks that make the plugins use what init generates: the preset rule files, a
 * coverage rule that fails the build, and JUnit 5 support for mutation testing. Each block is
 * rendered for the Groovy or the Kotlin DSL.
 */
final class GradleQualityBlocks {
  static final String COVERAGE_TASK = "jacocoTestCoverageVerification";
  static final String PITEST_JUNIT5 = "junit5PluginVersion";
  private static final String PITEST_JUNIT5_VERSION = "1.2.3";
  private static final Map<String, Double> DEFAULT_MINIMUMS = Map.of("LINE", 0.80);
  private static final int LIMIT_LENGTH = 128;

  private GradleQualityBlocks() {}

  /** One limit per counter, the preset's minimums or a line ratio of 0.80 without a preset. */
  static String coverage(final boolean kotlin, final QualityPreset preset, final String version) {
    final Map<String, Double> minimums =
        new TreeMap<>(preset == null ? DEFAULT_MINIMUMS : preset.rules().jacocoMinimums());
    final StringBuilder limits = new StringBuilder(LIMIT_LENGTH * minimums.size());
    minimums.forEach(
        (counter, minimum) ->
            limits
                .append("            limit {\n                counter = ")
                .append(quote(kotlin, counter))
                .append("\n                minimum = ")
                .append(kotlin ? "\"" + minimum + "\".toBigDecimal()" : minimum.toString())
                .append("\n            }\n"));
    // The Kotlin DSL reaches tasks through their type-safe accessor on tasks.
    return "jacoco {\n"
        + GradleEngineBlocks.toolVersion(kotlin, version)
        + "}\n"
        + (kotlin ? "tasks." : "")
        + COVERAGE_TASK
        + " {\n    violationRules {\n        rule {\n"
        + limits
        + "        }\n    }\n}\n"
        + GradleCoverageData.block(kotlin);
  }

  /**
   * PIT needs its JUnit 5 plugin to find JUnit Jupiter tests; the Gradle plugin's default PIT
   * cannot read Java 25 class files, so the engine version is pinned. The minimum score is the
   * gate's, summed over the modules, so the build sets no PIT threshold of its own. The task always
   * runs: Gradle would otherwise keep an unchanged run's report, which the gate reads as stale.
   */
  static String pitest(final boolean kotlin, final String version) {
    return "pitest {\n    pitestVersion = "
        + quote(kotlin, version)
        + "\n    "
        + PITEST_JUNIT5
        + " = "
        + quote(kotlin, PITEST_JUNIT5_VERSION)
        + "\n    outputFormats = "
        + (kotlin ? "listOf(\"XML\", \"HTML\")" : "['XML', 'HTML']")
        + "\n}\n"
        + (kotlin
            ? "tasks.named(\"pitest\") { outputs.upToDateWhen { false } }"
            : "tasks.named('pitest') { outputs.upToDateWhen { false } }");
  }

  static String quote(final boolean kotlin, final String value) {
    return kotlin ? "\"" + value + "\"" : "'" + value + "'";
  }
}
