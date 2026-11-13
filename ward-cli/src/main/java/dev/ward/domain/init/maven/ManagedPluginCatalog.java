package dev.ward.domain.init.maven;

import dev.ward.domain.init.ManagedPluginCandidate;
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
  private static final int JDK_25 = 25;
  private static final String MAVEN = "3.6";
  private static final String PIT = "pitest-maven";

  private static final List<ManagedPluginCandidate> ROWS =
      List.of(
          row("jacoco-maven-plugin", "0.8.13", JDK_8, JDK_25),
          row(PIT, "1.17.4", JDK_8, JDK_10),
          row(PIT, "1.30.1", JDK_11, JDK_25),
          row("maven-enforcer-plugin", "3.5.0", JDK_8, JDK_25),
          row("sonar-maven-plugin", "3.11.0.3922", JDK_8, JDK_10),
          row("sonar-maven-plugin", "5.1.0.4751", JDK_11, JDK_25));

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
