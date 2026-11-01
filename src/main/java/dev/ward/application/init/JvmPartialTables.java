package dev.ward.application.init;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The partial-run tables of the Maven and Gradle gates that can be scoped to edited files. */
final class JvmPartialTables {
  /** The gates that have a partial table. */
  static final Set<String> GATES = Set.of("checkstyle", "pmd", "format");

  private static final List<String> JAVA_SOURCES = List.of("src/**/*.java");
  private static final List<String> MAVEN_BUILD = List.of("pom.xml");
  private static final List<String> GRADLE_BUILD = List.of("build.gradle*", "settings.gradle*");
  private static final String GRADLE_PATHS = "-PwardPaths={paths}";
  private static final String COMMA = ",";

  private JvmPartialTables() {}

  /** Returns the partial-run table of a Maven or Gradle gate, or empty for any other gate. */
  static Optional<PartialCheckDefaults.Table> forGate(
      final String gate, final Project project, final QualityPreset preset) {
    final boolean gradle = project.buildTool() == BuildTool.GRADLE;
    final List<String> build = gradle ? GRADLE_BUILD : MAVEN_BUILD;
    return switch (gate) {
      case "checkstyle" -> Optional.of(checkstyle(gradle, preset, build));
      case "pmd" -> Optional.of(pmd(gradle, preset, build));
      case "format" -> Optional.of(format(gradle, build));
      default -> Optional.empty();
    };
  }

  /** Checkstyle's {@code includes} matches source-relative paths, so only the file name works. */
  private static PartialCheckDefaults.Table checkstyle(
      final boolean gradle, final QualityPreset preset, final List<String> build) {
    final List<String> config = withRuleFile(preset, "config/checkstyle.xml", build);
    if (gradle) {
      return gradleFiltered(preset, config);
    }
    return new PartialCheckDefaults.Table(
        JAVA_SOURCES, config, List.of("-Dcheckstyle.includes={paths}"), "**/{name}", COMMA);
  }

  /** The Maven PMD plugin has no property for {@code includes}; it runs in full, cached. */
  private static PartialCheckDefaults.Table pmd(
      final boolean gradle, final QualityPreset preset, final List<String> build) {
    final List<String> config = withRuleFile(preset, "config/pmd-ruleset.xml", build);
    return gradle
        ? gradleFiltered(preset, config)
        : PartialCheckDefaults.Table.whole(JAVA_SOURCES, config);
  }

  /**
   * Spotless matches {@code spotlessFiles} against absolute paths. Gradle's single-file mode
   * applies formatting instead of checking it, so Gradle runs in full.
   */
  private static PartialCheckDefaults.Table format(final boolean gradle, final List<String> build) {
    final List<String> config = gradle ? List.of(build.getFirst()) : build;
    if (gradle) {
      return PartialCheckDefaults.Table.whole(JAVA_SOURCES, config);
    }
    return new PartialCheckDefaults.Table(
        JAVA_SOURCES, config, List.of("-DspotlessFiles={paths}"), ".*/{path}", COMMA);
  }

  /** Only the Gradle blocks init writes with a preset read {@code wardPaths}. */
  private static PartialCheckDefaults.Table gradleFiltered(
      final QualityPreset preset, final List<String> config) {
    if (preset == null) {
      return PartialCheckDefaults.Table.whole(JAVA_SOURCES, config);
    }
    return new PartialCheckDefaults.Table(
        JAVA_SOURCES, config, List.of(GRADLE_PATHS), "{path}", COMMA);
  }

  private static List<String> withRuleFile(
      final QualityPreset preset, final String ruleFile, final List<String> build) {
    final List<String> config = new ArrayList<>(build.size() + 1);
    if (preset != null) {
      config.add(ruleFile);
    }
    config.addAll(build);
    return config;
  }
}
