package dev.ward.infrastructure.analysis.jvm;

import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds the JDK whose {@code lib/modules} image holds the JDK types, so that a native binary reads
 * the same types as a run on a JDK. The order is {@code JAVA_HOME}, the {@code java} on {@code
 * PATH}, {@code /usr/libexec/java_home} on macOS, then the running JDK.
 */
public final class JdkLocator {
  private static final Logger LOGGER = Logger.getLogger(JdkLocator.class.getName());
  private static final String MAC_JAVA_HOME = "/usr/libexec/java_home";

  private final CommandExecutor executor;
  private final UnaryOperator<String> environment;
  private final Optional<String> runningHome;
  private final String osName;

  /**
   * Creates the locator.
   *
   * @param executor runs {@code java_home}
   * @param environment reads an environment variable, null when unset
   * @param runningHome the {@code java.home} of this process
   * @param osName the {@code os.name} of this machine
   */
  public JdkLocator(
      final CommandExecutor executor,
      final UnaryOperator<String> environment,
      final Optional<String> runningHome,
      final String osName
  ) {
    this.executor = executor;
    this.environment = environment;
    this.runningHome = runningHome;
    this.osName = osName;
  }

  /** Returns the home of the first JDK with a module image, or empty when there is none. */
  public Optional<Path> find() {
    final Optional<Path> found =
        JvmClassLibraries.jdkHome(
            Optional.ofNullable(environment.apply("JAVA_HOME")),
            Optional.empty()
        )
            .or(this::onPath)
            .or(this::macJavaHome)
            .or(() -> JvmClassLibraries.jdkHome(Optional.empty(), runningHome));
    LOGGER.log(Level.FINE, "event=jdk-located found={0}", found.isPresent());
    return found;
  }

  private Optional<Path> onPath() {
    final String path = environment.apply("PATH");
    if (path == null) {
      return Optional.empty();
    }
    return java.util.Arrays.stream(path.split(File.pathSeparator))
        .filter(entry -> !entry.isBlank())
        .map(JdkLocator::homeOf)
        .flatMap(Optional::stream)
        .findFirst();
  }

  /** The JDK that a {@code bin/java} launcher belongs to, following links. */
  private static Optional<Path> homeOf(final String pathEntry) {
    try {
      final Path real = Path.of(pathEntry).resolve("java").toRealPath();
      final Path home = real.getParent() == null ? null : real.getParent().getParent();
      return Optional.ofNullable(home)
          .filter(candidate -> Files.isRegularFile(candidate.resolve("lib").resolve("modules")));
    } catch (IOException | InvalidPathException absent) {
      return Optional.empty();
    }
  }

  private Optional<Path> macJavaHome() {
    if (!osName.toLowerCase(Locale.ROOT).contains("mac")) {
      return Optional.empty();
    }
    final CommandResult result = executor.execute(List.of(MAC_JAVA_HOME), Path.of("."));
    if (result.exitCode() != 0) {
      return Optional.empty();
    }
    return JvmClassLibraries.jdkHome(Optional.of(result.stdout().strip()), Optional.empty());
  }
}
