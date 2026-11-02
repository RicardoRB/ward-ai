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
  static final String CHECKSTYLE_CONFIG = "config/checkstyle.xml";
  static final String PMD_RULESET = "config/pmd-ruleset.xml";
  static final String COVERAGE_TASK = "jacocoTestCoverageVerification";
  static final String PITEST_JUNIT5 = "junit5PluginVersion";
  private static final String PITEST_JUNIT5_VERSION = "1.2.3";
  private static final Map<String, Double> DEFAULT_MINIMUMS = Map.of("LINE", 0.80);
  private static final int LIMIT_LENGTH = 128;

  private GradleQualityBlocks() {}

  static String enforcement(final boolean kotlin) {
    return kotlin
        ? "tasks.named(\"check\") { dependsOn(\"checkstyleMain\") }"
        : "tasks.named('check') { dependsOn 'checkstyleMain' }";
  }

  static String archunit(final boolean kotlin, final String version) {
    final String coordinates = "com.tngtech.archunit:archunit-junit5:" + version;
    return kotlin
        ? "dependencies { testImplementation(\"" + coordinates + "\") }"
        : "dependencies { testImplementation '" + coordinates + "' }";
  }

  /**
   * OSS Index needs credentials, so it stays off until the user opts in through the environment.
   */
  static String dependencyCheck(final boolean kotlin) {
    return "dependencyCheck {\n    nvd { apiKey = System.getenv("
        + quote(kotlin, "NVD_API_KEY")
        + ") }\n    analyzers {\n        ossIndex {\n            enabled = false\n"
        + "            username = System.getenv("
        + quote(kotlin, "OSS_INDEX_USERNAME")
        + ")\n            password = System.getenv("
        + quote(kotlin, "OSS_INDEX_PASSWORD")
        + ")\n        }\n    }\n}";
  }

  /**
   * Like the Maven plugins init configures, Checkstyle and PMD analyze main sources only, so the
   * {@code check} task does not apply production rules to tests.
   */
  static String checkstyle(final boolean kotlin, final String version, final boolean module) {
    return "checkstyle {\n"
        + GradleEngineBlocks.toolVersion(kotlin, version)
        + "    configFile = "
        + (module ? "rootProject.file" : "file")
        + "("
        + quote(kotlin, CHECKSTYLE_CONFIG)
        + ")\n"
        + mainSourceSetOnly(kotlin)
        + "}\n"
        + GradlePathFilterBlock.render(kotlin, "checkstyleMain", "Checkstyle", module);
  }

  /** {@code ruleSets} is cleared so Gradle's default rule set does not run beside the preset's. */
  static String pmd(final boolean kotlin, final String version, final boolean module) {
    return "pmd {\n"
        + GradleEngineBlocks.toolVersion(kotlin, version)
        + "    ruleSetFiles = "
        + (module ? "rootProject.files" : "files")
        + "("
        + quote(kotlin, PMD_RULESET)
        + ")\n    ruleSets = "
        + (kotlin ? "listOf()" : "[]")
        + "\n"
        + mainSourceSetOnly(kotlin)
        + "}\n"
        + GradlePathFilterBlock.render(kotlin, "pmdMain", "Pmd", module);
  }

  private static String mainSourceSetOnly(final boolean kotlin) {
    return kotlin
        ? "    sourceSets = listOf(project.sourceSets.main.get())\n"
        : "    sourceSets = [project.sourceSets.main]\n";
  }

  static String spotless(final boolean kotlin, final String formatterVersion) {
    return "spotless {\n    java {\n        googleJavaFormat("
        + quote(kotlin, formatterVersion)
        + ")\n    }\n}";
  }

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
