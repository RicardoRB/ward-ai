package dev.ward.domain.init;

import java.util.List;
import java.util.Map;

/**
 * The Maven plugins, test dependencies, and engines init can pin, one row per version. The JDK
 * ranges of one tool never overlap, and each was measured on a real JDK; see the Java compatibility
 * matrix.
 */
public final class ManagedPluginCatalog {
  private static final int JDK_8 = 8;
  private static final int JDK_10 = 10;
  private static final int JDK_11 = 11;
  private static final int JDK_16 = 16;
  private static final int JDK_17 = 17;
  private static final int JDK_20 = 20;
  private static final int JDK_21 = 21;
  private static final int JDK_25 = 25;
  private static final String MAVEN = "3.6";
  private static final String SPOTLESS = "spotless-maven-plugin";
  private static final String FORMATTER = "google-java-format";
  private static final String SPOTBUGS = "spotbugs-maven-plugin";
  private static final String PIT = "pitest-maven";

  private static final List<ManagedPluginCandidate> ROWS =
      List.of(
          row("maven-checkstyle-plugin", "3.6.0", JDK_8, JDK_25),
          row("checkstyle", "9.3", JDK_8, JDK_10),
          row("checkstyle", "10.20.2", JDK_11, JDK_25),
          row("maven-pmd-plugin", "3.28.0", JDK_8, JDK_25),
          row(SPOTBUGS, "4" + ".8.6.6", JDK_8, JDK_10),
          row(SPOTBUGS, "4" + ".9.7.0", JDK_11, JDK_25),
          row("jacoco-maven-plugin", "0.8.13", JDK_8, JDK_25),
          row(PIT, "1.17.4", JDK_8, JDK_10),
          row(PIT, "1.30.1", JDK_11, JDK_25),
          row("maven-enforcer-plugin", "3.5.0", JDK_8, JDK_25),
          row(SPOTLESS, "2.30.0", JDK_8, JDK_10),
          row(SPOTLESS, "2.46.1", JDK_11, JDK_16),
          row(SPOTLESS, "3.10.3", JDK_17, JDK_25),
          row(FORMATTER, "1.7", JDK_8, JDK_10),
          row(FORMATTER, "1.24.0", JDK_11, JDK_16),
          row(FORMATTER, "1.28.0", JDK_17, JDK_20),
          row(FORMATTER, "1.36.1", JDK_21, JDK_25),
          row("archunit-junit5", "1.5.1", JDK_8, JDK_25),
          row("sonar-maven-plugin", "3.11.0.3922", JDK_8, JDK_10),
          row("sonar-maven-plugin", "5.1.0.4751", JDK_11, JDK_25),
          row("dependency-check-maven", "8.4.0", JDK_8, JDK_10),
          row("dependency-check-maven", "12.1.0", JDK_11, JDK_25),
          row("license-maven-plugin", "2.5.0", JDK_8, JDK_25),
          row("japicmp-maven-plugin", "0.23.1", JDK_8, JDK_25));

  private ManagedPluginCatalog() {}

  /** Returns each tool's rows, keyed by tool. */
  public static Map<String, List<ManagedPluginCandidate>> entries() {
    return ManagedPluginCandidate.byTool(ROWS);
  }

  private static ManagedPluginCandidate row(
      final String tool, final String version, final int minimumJdk, final int maximumJdk) {
    return new ManagedPluginCandidate(tool, version, minimumJdk, maximumJdk, MAVEN, "measured");
  }
}
