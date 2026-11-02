package dev.ward.infrastructure.init.gradle;

import dev.ward.domain.gate.SupportedQualityGates;
import java.util.Set;

/**
 * The blocks init appends to an Android module script. An Android module applies no JVM plugin, so
 * only the Android gates' configuration goes there, never the Checkstyle, PMD or SpotBugs blocks.
 */
final class GradleAndroidBlocks {
  static final String MARKER = GradleBuildBlocks.MARKER;
  private static final String UNIT_TEST_COVERAGE = "enableUnitTestCoverage";

  private GradleAndroidBlocks() {}

  /** Appends the Android gate blocks that {@code selected} asks for and the script lacks. */
  static void append(final GradleBuildFileEdit edit, final Set<String> selected) {
    if (selected.contains(SupportedQualityGates.ANDROID_COVERAGE)
        && !edit.contains(UNIT_TEST_COVERAGE)) {
      final boolean kotlin = edit.buildFile().toString().endsWith(".kts");
      edit.append(
          "\n" + MARKER + "\n" + unitTestCoverage(kotlin),
          SupportedQualityGates.ANDROID_COVERAGE,
          "");
    }
  }

  /**
   * Turns on AGP's JaCoCo report for the debug unit tests. The lint and unit-test gates need no
   * block: they run AGP's own tasks.
   */
  private static String unitTestCoverage(final boolean kotlin) {
    return kotlin
        ? "android {\n    buildTypes {\n        getByName(\"debug\") {\n"
            + "            enableUnitTestCoverage = true\n        }\n    }\n}\n"
        : "android {\n    buildTypes {\n        debug {\n"
            + "            enableUnitTestCoverage = true\n        }\n    }\n}\n";
  }
}
