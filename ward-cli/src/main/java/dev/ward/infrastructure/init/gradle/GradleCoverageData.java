package dev.ward.infrastructure.init.gradle;

/** The Gradle task that makes the coverage gate fail a module whose tests left no coverage data. */
final class GradleCoverageData {
  static final String NO_EXECUTION_DATA =
      "No JaCoCo execution data in this module: add tests so coverage can be checked.";

  private GradleCoverageData() {}

  /**
   * Gradle skips the verification when no test wrote JaCoCo data, so a module without tests would
   * pass. This task runs the tests first and fails when they left no data, and the verification
   * depends on it.
   */
  static String block(final boolean kotlin) {
    return kotlin
        ? "tasks.register(\"wardCoverageData\") {\n"
            + "    dependsOn(\"test\")\n"
            + "    doLast {\n"
            + "        val exec = tasks.named<org.gradle.api.tasks.testing.Test>(\"test\").get()\n"
            + "            .extensions.getByType(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class.java)\n"
            + "            .destinationFile\n"
            + "        check(exec != null && exec.exists()) { \""
            + NO_EXECUTION_DATA
            + "\" }\n"
            + "    }\n"
            + "}\n"
            + "tasks.named(\""
            + GradleQualityBlocks.COVERAGE_TASK
            + "\") { dependsOn(\"wardCoverageData\") }"
        : "tasks.register('wardCoverageData') {\n"
            + "    dependsOn 'test'\n"
            + "    doLast {\n"
            + "        def exec = tasks.named('test').get().extensions"
            + ".getByType(org.gradle.testing.jacoco.plugins.JacocoTaskExtension).destinationFile\n"
            + "        if (!exec.exists()) {\n"
            + "            throw new org.gradle.api.GradleException('"
            + NO_EXECUTION_DATA
            + "')\n"
            + "        }\n"
            + "    }\n"
            + "}\n"
            + "tasks.named('"
            + GradleQualityBlocks.COVERAGE_TASK
            + "') { dependsOn 'wardCoverageData' }";
  }
}
