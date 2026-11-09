package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

/**
 * The fixtures of one implemented rule in one language: the directory {@code
 * rules/<language>/<name>/} holds {@code violating.<ext>}, {@code expected.txt} (one {@code
 * line:column} per expected finding, in order) and {@code passing.<ext>}. A rule may add cases as
 * {@code violating-<case>.<ext>} with {@code expected-<case>.txt}, and {@code
 * passing-<case>.<ext>}; a TypeScript case may use {@code .tsx} instead of {@code .ts}, which turns
 * JSX on. A {@code types.json} next to them holds type facts for a rule that needs types; see
 * {@link FixtureTypeFacts}.
 *
 * @param language the language of the rule
 * @param name the rule name, the last part of its id
 * @param directory the fixture directory
 */
record RuleFixtureSet(AnalysisLanguage language, String name, Path directory) {

  private static final Map<AnalysisLanguage, String> EXTENSIONS =
      Map.of(
          AnalysisLanguage.JAVA, "java",
          AnalysisLanguage.KOTLIN, "kt",
          AnalysisLanguage.TYPESCRIPT, "ts",
          AnalysisLanguage.JAVASCRIPT, "js",
          AnalysisLanguage.PYTHON, "py",
          AnalysisLanguage.SWIFT, "swift");

  /** Returns the root of the fixtures, {@code rules} on the test classpath. */
  static Path root() {
    final URL url = RuleFixtureSet.class.getResource("/rules");
    if (url == null) {
      throw new IllegalStateException("missing fixture directory rules");
    }
    try {
      return Path.of(url.toURI());
    } catch (final URISyntaxException exception) {
      throw new IllegalStateException(exception);
    }
  }

  /** Returns the fixtures that {@code row} has in {@code language}, under {@code root}. */
  static RuleFixtureSet of(final Path root, final CatalogRow row, final AnalysisLanguage language) {
    final String id = row.idFor(language);
    final String name = id.substring(id.indexOf('/') + 1);
    return new RuleFixtureSet(language, name, root.resolve(CatalogRow.key(language)).resolve(name));
  }

  /** Returns the file extension of {@code language}'s sources, without the dot. */
  static String extension(final AnalysisLanguage language) {
    return EXTENSIONS.get(language);
  }

  /** Returns the case names: the empty text for the default case, then each added case. */
  List<String> cases() {
    final List<String> cases = new ArrayList<>();
    cases.add("");
    try (Stream<Path> files = Files.list(directory)) {
      files
          .map(file -> file.getFileName().toString())
          .filter(file -> file.startsWith("violating-"))
          .map(file -> file.substring("violating-".length(), file.lastIndexOf('.')))
          .sorted()
          .forEach(cases::add);
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
    return cases;
  }

  /** Returns the violating source file of {@code caseName}. */
  Path violating(final String caseName) {
    return source("violating", caseName);
  }

  /** Returns the passing source file of {@code caseName}. */
  Path passing(final String caseName) {
    return source("passing", caseName);
  }

  /** Returns the expected positions of {@code caseName}, as {@code line:column} texts. */
  List<String> expected(final String caseName) {
    final Path file = directory.resolve("expected" + suffix(caseName) + ".txt");
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
          .filter(line -> !line.isBlank())
          .toList();
    } catch (final IOException exception) {
      throw new UncheckedIOException("missing " + file, exception);
    }
  }

  /**
   * Returns the option values of {@code caseName} from its {@code options-<case>.toml}, as {@link
   * dev.ward.analysis.domain.rule.RuleOptions#apply} takes them; empty when the case has none.
   */
  Map<String, Object> options(final String caseName) {
    final Path file = directory.resolve("options" + suffix(caseName) + ".toml");
    if (!Files.isRegularFile(file)) {
      return Map.of();
    }
    try {
      final TomlParseResult toml = Toml.parse(Files.readString(file, StandardCharsets.UTF_8));
      final Map<String, Object> values = new LinkedHashMap<>();
      for (final String key : toml.keySet()) {
        values.put(key, plain(toml.get(key)));
      }
      return values;
    } catch (final IOException exception) {
      throw new UncheckedIOException("missing " + file, exception);
    }
  }

  private static Object plain(final Object value) {
    return value instanceof TomlArray array ? array.toList() : value;
  }

  /** Returns the type facts file of the rule, when it has one. */
  Optional<Path> typeFacts() {
    final Path file = directory.resolve("types.json");
    return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
  }

  /** Returns whether the directory holds the default violating and passing fixtures. */
  boolean complete() {
    return Files.isRegularFile(violating(""))
        && Files.isRegularFile(passing(""))
        && Files.isRegularFile(directory.resolve("expected.txt"));
  }

  /**
   * A TypeScript case with a {@code .tsx} file uses it, so that JSX is recognized; else {@code
   * .ts}.
   */
  private Path source(final String kind, final String caseName) {
    final String base = kind + suffix(caseName);
    final Path tsx = directory.resolve(base + ".tsx");
    if (language == AnalysisLanguage.TYPESCRIPT && Files.isRegularFile(tsx)) {
      return tsx;
    }
    return directory.resolve(base + "." + extension(language));
  }

  private static String suffix(final String caseName) {
    return caseName.isEmpty() ? "" : "-" + caseName;
  }
}
