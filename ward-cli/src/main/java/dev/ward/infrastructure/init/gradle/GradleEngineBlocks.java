package dev.ward.infrastructure.init.gradle;

/** The {@code toolVersion} lines that pin the engine a Gradle quality plugin runs. */
final class GradleEngineBlocks {
  private GradleEngineBlocks() {}

  static String toolVersion(final boolean kotlin, final String version) {
    return "    toolVersion = " + GradleQualityBlocks.quote(kotlin, version) + "\n";
  }
}
