package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.BuiltinNames;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads the built-in names of a language from classpath resources, one file per environment under a
 * directory, one name per line. The base environments always apply; the requested ones add to them,
 * or the defaults do when none is requested.
 */
public final class ClasspathBuiltinNames implements BuiltinNames {
  private static final Logger LOGGER = Logger.getLogger(ClasspathBuiltinNames.class.getName());
  private static final String ROOT = "/dev/ward/analysis/lang/";

  private final String directory;
  private final List<String> base;
  private final List<String> defaults;
  private final Map<String, Set<String>> loaded = new ConcurrentHashMap<>();

  /**
   * Creates the reader.
   *
   * @param directory the resource directory under {@code dev/ward/analysis/lang/}, such as {@code
   *     node/builtins}
   * @param base the environments that always apply
   * @param defaults the environments that apply when none is requested
   */
  public ClasspathBuiltinNames(
      final String directory, final List<String> base, final List<String> defaults) {
    this.directory = directory;
    this.base = List.copyOf(base);
    this.defaults = List.copyOf(defaults);
  }

  /** The names of Python. */
  public static ClasspathBuiltinNames python() {
    return new ClasspathBuiltinNames("python/builtins", List.of("python"), List.of());
  }

  /** The globals of JavaScript: ECMAScript, plus {@code browser} and {@code node} by default. */
  public static ClasspathBuiltinNames javascript() {
    return new ClasspathBuiltinNames(
        "node/builtins", List.of("es2024"), List.of("browser", "node"));
  }

  /** The globals of TypeScript: those of JavaScript, plus the predefined types and utilities. */
  public static ClasspathBuiltinNames typescript() {
    return new ClasspathBuiltinNames(
        "node/builtins", List.of("es2024", "typescript"), List.of("browser", "node"));
  }

  /** The names {@code java.lang} provides. */
  public static ClasspathBuiltinNames java() {
    return new ClasspathBuiltinNames("jvm/builtins", List.of("java"), List.of());
  }

  /** The names the Kotlin standard library imports by default. */
  public static ClasspathBuiltinNames kotlin() {
    return new ClasspathBuiltinNames("jvm/builtins", List.of("kotlin"), List.of());
  }

  /** The names the Swift standard library provides. */
  public static ClasspathBuiltinNames swift() {
    return new ClasspathBuiltinNames("swift/builtins", List.of("swift"), List.of());
  }

  @Override
  public Set<String> of(final Set<String> environments) {
    final Set<String> names = new LinkedHashSet<>();
    base.forEach(environment -> names.addAll(environment(environment)));
    final Iterable<String> requested = environments.isEmpty() ? defaults : environments;
    for (final String environment : requested) {
      names.addAll(environment(environment));
    }
    return Set.copyOf(names);
  }

  private Set<String> environment(final String environment) {
    return loaded.computeIfAbsent(environment, this::read);
  }

  private Set<String> read(final String environment) {
    final String resource = ROOT + directory + "/" + environment + ".txt";
    try (InputStream stream = ClasspathBuiltinNames.class.getResourceAsStream(resource)) {
      if (stream == null) {
        throw new AnalysisFailedException(
            "no built-in names for environment '" + environment + "'");
      }
      final Set<String> names = new LinkedHashSet<>();
      for (final String line :
          new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
        if (!line.isBlank()) {
          names.add(line.strip());
        }
      }
      if (LOGGER.isLoggable(Level.FINE)) {
        LOGGER.fine(
            "event=builtin-names-loaded environment=" + environment + " count=" + names.size());
      }
      return Set.copyOf(names);
    } catch (final IOException exception) {
      throw new AnalysisFailedException(
          "the built-in names of environment '" + environment + "' could not be read", exception);
    }
  }
}
