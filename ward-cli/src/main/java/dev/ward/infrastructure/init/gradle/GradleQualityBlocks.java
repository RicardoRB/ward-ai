package dev.ward.infrastructure.init.gradle;

/**
 * Gradle extension blocks that make the plugins use what init generates: the preset rule files, the
 * JaCoCo setup that writes the report Ward checks, and JUnit 5 support for mutation testing. Each
 * block is rendered for the Groovy or the Kotlin DSL.
 */
final class GradleQualityBlocks {
  static final String COVERAGE_TASK = "jacocoTestReport";
  static final String PITEST_JUNIT5 = "junit5PluginVersion";
  private static final String PITEST_JUNIT5_VERSION = "1.2.3";

  private GradleQualityBlocks() {}

  /**
   * The JaCoCo engine version and the task that fails a module whose tests left no execution data.
   * The minimum is not here: Ward checks it against the XML report in {@code ward.toml}.
   */
  static String coverage(final boolean kotlin, final String version) {
    return "jacoco {\n"
        + GradleEngineBlocks.toolVersion(kotlin, version)
        + "}\n"
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
