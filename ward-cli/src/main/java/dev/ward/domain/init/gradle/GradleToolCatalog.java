package dev.ward.domain.init.gradle;

import dev.ward.domain.init.ManagedPluginCandidate;
import java.util.List;
import java.util.Map;

/**
 * The Gradle plugins and engines init can pin, one row per version, keyed by plugin id or engine
 * name. The JDK ranges of one tool never overlap, and each was measured on a real JDK and Gradle;
 * see the Java compatibility matrix.
 */
public final class GradleToolCatalog {
  /** The PIT plugin id. */
  public static final String PITEST = "info.solidsoft.pitest";

  /** The JaCoCo engine. */
  public static final String JACOCO_ENGINE = "jacoco";

  /** The PIT engine. */
  public static final String PITEST_ENGINE = "pitest";

  private static final int JDK_8 = 8;
  private static final int JDK_10 = 10;
  private static final int JDK_11 = 11;
  private static final int JDK_25 = 25;
  private static final String ANY_GRADLE = "6.1.1";

  private static final List<ManagedPluginCandidate> ROWS =
      List.of(
          row(JACOCO_ENGINE, "0.8.13", JDK_8, JDK_25, ANY_GRADLE),
          row(PITEST, "1.15.0", JDK_8, JDK_10, "6.4"),
          row(PITEST, "1.19.0", JDK_11, JDK_25, "8.4"),
          row(PITEST_ENGINE, "1.17.4", JDK_8, JDK_10, "6.4"),
          row(PITEST_ENGINE, "1.30.1", JDK_11, JDK_25, "6.4"),
          row("org.sonarqube", "5.1.0.4882", JDK_11, JDK_25, ANY_GRADLE));

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
