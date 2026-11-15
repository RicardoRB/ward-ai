package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.ArchitectureRule;
import dev.ward.application.gate.ArchitectureFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;

/**
 * Reads {@code ward-architecture.toml}: a {@code version} and {@code [[rule]]} tables. It checks
 * the whole shape and reports each problem with the file, the rule and the key. It starts no
 * process and reads no other file.
 */
final class ArchitectureFileReader {
  /** The keys of the file; the schema in the website lists the same ones. */
  static final Set<String> TOP_LEVEL = Set.of("version", "rule");

  private static final Logger LOGGER = Logger.getLogger(ArchitectureFileReader.class.getName());
  private static final String VERSION = "version";
  private static final String RULE = "rule";
  private static final long SUPPORTED_VERSION = 1;

  private final String file;
  private final ArchitectureProblems problems;

  private ArchitectureFileReader(final String file) {
    this.file = file;
    this.problems = new ArchitectureProblems(file);
  }

  /**
   * Reads the file {@code relative} under {@code root}, or {@code ward-architecture.toml} when it
   * is {@code null}. Returns empty when the default file does not exist.
   */
  static Optional<ArchitectureFile> read(final Path root, final String relative) {
    final String name = relative == null ? ArchitectureFile.DEFAULT_NAME : relative;
    final Path path = root.resolve(name).normalize();
    if (!path.startsWith(root.normalize())) {
      return Optional.of(ArchitectureFile.unusable(name, "the file must stay inside the project"));
    }
    if (!Files.isRegularFile(path)) {
      return relative == null
          ? Optional.empty()
          : Optional.of(ArchitectureFile.unusable(name, "the file does not exist"));
    }
    try {
      LOGGER.log(Level.FINE, "event=architecture-file-read file={0}", name);
      return Optional.of(new ArchitectureFileReader(name).parse(Toml.parse(path)));
    } catch (final IOException exception) {
      return Optional.of(ArchitectureFile.unusable(name, "the file could not be read"));
    }
  }

  private ArchitectureFile parse(final TomlParseResult toml) {
    if (toml.hasErrors()) {
      for (final TomlParseError error : toml.errors()) {
        problems.add("not valid TOML at " + error.position() + ": " + error.getMessage());
      }
      return new ArchitectureFile(file, List.of(), problems.all());
    }
    for (final String key : toml.keySet()) {
      if (!TOP_LEVEL.contains(key)) {
        problems.add("", key, "unknown key; expected " + List.of(VERSION, RULE));
      }
    }
    if (!Long.valueOf(SUPPORTED_VERSION).equals(toml.get(VERSION))) {
      problems.add("", VERSION, "expected " + SUPPORTED_VERSION + ", got " + toml.get(VERSION));
    }
    final List<ArchitectureRule> rules = new ArchitectureRuleReader(problems).rules(toml);
    return new ArchitectureFile(file, problems.count() == 0 ? rules : List.of(), problems.all());
  }
}
