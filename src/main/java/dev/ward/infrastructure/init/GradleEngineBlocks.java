package dev.ward.infrastructure.init;

/** The {@code toolVersion} lines that pin the engine a Gradle quality plugin runs. */
final class GradleEngineBlocks {
  private GradleEngineBlocks() {}

  /** The default SpotBugs engine cannot read Java 25 class files, so the engine is pinned. */
  static String spotbugs(final boolean kotlin, final String version) {
    return "spotbugs {\n" + toolVersion(kotlin, version) + "}";
  }

  static String toolVersion(final boolean kotlin, final String version) {
    return "    toolVersion = " + GradleQualityBlocks.quote(kotlin, version) + "\n";
  }
}
