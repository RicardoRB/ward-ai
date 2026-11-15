package dev.ward.infrastructure.rule;

import dev.ward.application.gate.ArchitectureFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes {@code ward-architecture.toml} files and reads their problems, for the reader tests. */
final class ArchitectureFiles {
  static final String VALID_RULE =
      """
      [[rule]]
      name = "domain-is-pure"
      select = { directories = ["domain"] }
      must-not-depend-on = { directories = ["infrastructure", "adapter"] }
      because = "the domain must not know its adapters"
      """;

  private ArchitectureFiles() {}

  /** A version 1 file with one valid rule and {@code extra} lines at the end of the rule. */
  static String rule(final String extra) {
    return "version = 1\n[[rule]]\nname = \"r\"\nselect = { directories = [\"a\"] }\n"
        + "must-not-depend-on = { directories = [\"b\"] }\nbecause = \"x\"\n"
        + extra
        + "\n";
  }

  /** The problems of the file {@code relative} under {@code root}, or of the default file. */
  static List<String> problems(final Path root, final String relative) {
    final ArchitectureFile file = ArchitectureFileReader.read(root, relative).orElseThrow();
    return file.problems();
  }

  /** Writes {@code text} as the default file and returns its problems. */
  static List<String> problemsOf(final Path root, final String text) throws IOException {
    write(root, "ward-architecture.toml", text);
    return problems(root, null);
  }

  static void write(final Path root, final String name, final String text) throws IOException {
    final Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
