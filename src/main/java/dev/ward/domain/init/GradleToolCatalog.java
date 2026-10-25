package dev.ward.domain.init;

import java.util.List;
import java.util.Map;

/**
 * The Gradle plugins and engines init can pin, one row per version, keyed by plugin id or engine
 * name. The JDK ranges of one tool never overlap, and each was measured on a real JDK and Gradle;
 * see the Java compatibility matrix.
 */
public final class GradleToolCatalog {
  /** The Spotless plugin id. */
  public static final String SPOTLESS = "com.diffplug.spotless";

  /** The SpotBugs plugin id. */
  public static final String SPOTBUGS = "com.github.spotbugs";

  /** The PIT plugin id. */
  public static final String PITEST = "info.solidsoft.pitest";

  /** The Checkstyle engine. */
  public static final String CHECKSTYLE_ENGINE = "checkstyle";

  /** The PMD engine. */
  public static final String PMD_ENGINE = "pmd";

  /** The SpotBugs engine. */
  public static final String SPOTBUGS_ENGINE = "spotbugs";

  /** The JaCoCo engine. */
  public static final String JACOCO_ENGINE = "jacoco";

  /** The PIT engine. */
  public static final String PITEST_ENGINE = "pitest";

  /** The formatter Spotless runs. */
  public static final String FORMATTER = "google-java-format";

  /** The ArchUnit test dependency. */
  public static final String ARCHUNIT = "archunit";

  private static final int JDK_8 = 8;
  private static final int JDK_10 = 10;
  private static final int JDK_11 = 11;
  private static final int JDK_16 = 16;
  private static final int JDK_17 = 17;
  private static final int JDK_20 = 20;
  private static final int JDK_21 = 21;
  private static final int JDK_25 = 25;
  private static final String ANY_GRADLE = "6.1.1";

  private static final List<ManagedPluginCandidate> ROWS =
      List.of(
          row(SPOTLESS, "6.13.0", JDK_8, JDK_10, ANY_GRADLE),
          row(SPOTLESS, "7.2.1", JDK_11, JDK_16, ANY_GRADLE),
          row(SPOTLESS, "8.10.4", JDK_17, JDK_25, "8.1"),
          row(FORMATTER, "1.7", JDK_8, JDK_10, ANY_GRADLE),
          row(FORMATTER, "1.24.0", JDK_11, JDK_16, ANY_GRADLE),
          row(FORMATTER, "1.28.0", JDK_17, JDK_20, ANY_GRADLE),
          row(FORMATTER, "1.36.1", JDK_21, JDK_25, ANY_GRADLE),
          row(CHECKSTYLE_ENGINE, "9.3", JDK_8, JDK_10, ANY_GRADLE),
          row(CHECKSTYLE_ENGINE, "10.20.2", JDK_11, JDK_25, ANY_GRADLE),
          row(PMD_ENGINE, "7.17.0", JDK_8, JDK_25, "8.6"),
          row(SPOTBUGS, "6.0.26", JDK_8, JDK_25, "7.1"),
          row(SPOTBUGS_ENGINE, "4.8.6", JDK_8, JDK_10, "7.1"),
          row(SPOTBUGS_ENGINE, "4.9.7", JDK_11, JDK_25, "7.1"),
          row(JACOCO_ENGINE, "0.8.13", JDK_8, JDK_25, ANY_GRADLE),
          row(PITEST, "1.15.0", JDK_8, JDK_10, "6.4"),
          row(PITEST, "1.19.0", JDK_11, JDK_25, "8.4"),
          row(PITEST_ENGINE, "1.17.4", JDK_8, JDK_10, "6.4"),
          row(PITEST_ENGINE, "1.30.1", JDK_11, JDK_25, "6.4"),
          row(ARCHUNIT, "1.5.1", JDK_8, JDK_25, ANY_GRADLE),
          row("org.owasp.dependencycheck", "12.1.0", JDK_11, JDK_25, ANY_GRADLE),
          row("org.sonarqube", "5.1.0.4882", JDK_11, JDK_25, ANY_GRADLE),
          row("com.github.jk1.dependency-license-report", "3.1.4", JDK_8, JDK_25, ANY_GRADLE),
          row("me.champeau.gradle.japicmp", "0.4.6", JDK_8, JDK_25, ANY_GRADLE));

  private GradleToolCatalog() {}

  /** Returns each tool's rows, keyed by plugin id or engine name. */
  public static Map<String, List<ManagedPluginCandidate>> entries() {
    return ManagedPluginCandidate.byTool(ROWS);
  }

  private static ManagedPluginCandidate row(
      final String tool,
      final String version,
      final int minimumJdk,
      final int maximumJdk,
      final String minimumGradle) {
    return new ManagedPluginCandidate(
        tool, version, minimumJdk, maximumJdk, minimumGradle, "measured");
  }
}
