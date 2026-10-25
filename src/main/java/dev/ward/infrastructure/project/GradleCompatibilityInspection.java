package dev.ward.infrastructure.project;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.CompatibilityInspection;
import dev.ward.domain.init.CompatibilityProjectSnapshot;
import dev.ward.domain.init.ToolchainSnapshot;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Reads the Gradle version, the launcher JVM, and the Java release a Gradle build targets. A
 * release the patterns cannot read, such as one computed from a version catalog, is unknown, so
 * resolution uses the launcher JVM.
 */
public final class GradleCompatibilityInspection implements CompatibilityInspection {
  private static final Pattern GRADLE_VERSION =
      Pattern.compile("^Gradle\\s+([0-9][^\\s]*)", Pattern.MULTILINE);
  private static final Pattern LAUNCHER_JVM = Pattern.compile("Launcher JVM:\\s+([0-9][^\\s]*)");
  private static final Pattern PLUGIN =
      Pattern.compile(
          "id\\s*\\(?\\s*['\"]([\\w.-]+)['\"]\\s*\\)?\\s+version\\s+['\"]([^'\"]+)['\"]");
  private static final List<Pattern> RELEASES =
      List.of(
          Pattern.compile("JavaLanguageVersion\\.of\\(\\s*(\\d+)\\s*\\)"),
          Pattern.compile("release(?:\\.set\\(\\s*|\\s*=\\s*)(\\d+)"),
          Pattern.compile("sourceCompatibility\\s*=?\\s*JavaVersion\\.VERSION_(\\d+(?:_\\d+)?)"),
          Pattern.compile(
              "sourceCompatibility\\s*=?\\s*JavaVersion\\.toVersion\\(\\s*['\"]?([\\d.]+)"),
          Pattern.compile("sourceCompatibility\\s*=?\\s*['\"]?(\\d[\\d.]*)"));
  private final CommandExecutor commands;

  /** Creates the inspection; {@code commands} runs Gradle to read the toolchain. */
  @Inject
  public GradleCompatibilityInspection(final CommandExecutor commands) {
    this.commands = commands;
  }

  @Override
  public CompatibilityProjectSnapshot inspect(final Project project) {
    final Optional<String> version = versionOutput(project);
    final String script = buildScript(project.root());
    return new CompatibilityProjectSnapshot(
        project,
        new ToolchainSnapshot(
            version.flatMap(output -> match(LAUNCHER_JVM, output)).map(JavaVersions::feature),
            release(script),
            version.flatMap(output -> match(GRADLE_VERSION, output)),
            project.framework()),
        plugins(script));
  }

  /** Returns the release that {@code script} declares, if a known pattern finds one. */
  static Optional<Integer> release(final String script) {
    return RELEASES.stream()
        .map(pattern -> match(pattern, script))
        .flatMap(Optional::stream)
        .findFirst()
        .map(value -> JavaVersions.feature(value.replace('_', '.')));
  }

  /** Returns the plugin versions {@code script} declares, keyed by plugin id. */
  static Map<String, String> plugins(final String script) {
    final Map<String, String> plugins = new LinkedHashMap<>();
    final Matcher matcher = PLUGIN.matcher(script);
    while (matcher.find()) {
      plugins.put(matcher.group(1), matcher.group(2));
    }
    return plugins;
  }

  private Optional<String> versionOutput(final Project project) {
    final String executable = project.wrapperAvailable() ? "./gradlew" : "gradle";
    final CommandResult result = commands.execute(List.of(executable, "--version"), project.root());
    return result.succeeded() ? Optional.of(result.stdout()) : Optional.empty();
  }

  private static String buildScript(final Path root) {
    final Optional<Path> script =
        Stream.of("build.gradle.kts", "build.gradle")
            .map(root::resolve)
            .filter(Files::isRegularFile)
            .findFirst();
    if (script.isEmpty()) {
      return "";
    }
    try {
      return Files.readString(script.get());
    } catch (IOException e) {
      throw new WardException("Could not read " + script.get() + ": " + e.getMessage(), e);
    }
  }

  private static Optional<String> match(final Pattern pattern, final String text) {
    final Matcher matcher = pattern.matcher(text);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }
}
