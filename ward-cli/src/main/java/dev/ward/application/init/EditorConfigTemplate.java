package dev.ward.application.init;

import dev.ward.domain.init.FormatGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes the {@code .editorconfig} that the {@code ward} format engine reads, but only for a
 * project that has none: an existing file is the user's, and init never merges into it.
 */
final class EditorConfigTemplate {
  static final String FILE_NAME = ".editorconfig";
  static final String BEGIN = "# ward-owned: editorconfig v1 begin";
  static final String END = "# ward-owned: editorconfig v1 end";
  private static final String PRELUDE =
      "root = true\n\n[*]\nmax_line_length = 100\nindent_style = space\n"
          + "insert_final_newline = true\nward_max_blank_lines = 1\n";
  private static final int INDENT_FOUR = 4;
  private static final int INDENT_TWO = 2;
  private static final int CONTINUATION = 8;
  private static final Set<String> FOUR_SPACE = Set.of("java", "kt", "kts", "py", "swift");
  private static final Set<String> TWO_SPACE = Set.of("ts", "tsx", "js", "jsx", "mjs", "cjs");
  private static final Set<String> JVM_JAVA = Set.of("java", "kt", "kts");

  private EditorConfigTemplate() {}

  /** Returns the project's {@code .editorconfig}, which may not exist. */
  static Path path(final Project project) {
    return project.root().resolve(FILE_NAME);
  }

  /**
   * Returns the section that writes the project's {@code .editorconfig} with the Ward defaults for
   * its languages, when {@code ward-format} is selected and {@code existing} is empty. An existing
   * file is never merged into, so the result is empty for it. The file is written whole, so a file
   * that appears before the write blocks it.
   */
  static Optional<OwnedSection> section(
      final Project project, final List<InitGateOption> gates, final Optional<String> existing) {
    if (existing.isPresent()
        || gates.stream().noneMatch(gate -> FormatGates.WARD_FORMAT.equals(gate.id()))) {
      return Optional.empty();
    }
    return Optional.of(
        new OwnedSection(path(project), BEGIN, END, body(project.languages()), true));
  }

  /** Returns the text between the markers: the root flag, the shared defaults, then the groups. */
  static String body(final Set<Language> languages) {
    final List<String> extensions = extensions(languages);
    final StringBuilder body = new StringBuilder(PRELUDE);
    group(body, extensions, FOUR_SPACE, "indent_size = " + INDENT_FOUR);
    group(body, extensions, TWO_SPACE, "indent_size = " + INDENT_TWO);
    if (languages.contains(Language.JAVA) || languages.contains(Language.KOTLIN)) {
      group(body, extensions, JVM_JAVA, "ward_continuation_indent_size = " + CONTINUATION);
    }
    return body.toString();
  }

  /**
   * Returns the path globs of the source files of {@code languages}, such as {@code **}/*.java}.
   */
  static List<String> sourceGlobs(final Set<Language> languages) {
    return extensions(languages).stream().map(extension -> "**/*." + extension).toList();
  }

  /** Lists the extensions of the languages, so a group names only what the project has. */
  private static List<String> extensions(final Set<Language> languages) {
    final List<String> extensions = new ArrayList<>();
    if (languages.contains(Language.JAVA)) {
      extensions.add("java");
    }
    if (languages.contains(Language.KOTLIN)) {
      extensions.addAll(List.of("kt", "kts"));
    }
    if (languages.contains(Language.PYTHON)) {
      extensions.add("py");
    }
    if (languages.contains(Language.SWIFT)) {
      extensions.add("swift");
    }
    if (languages.contains(Language.TYPESCRIPT) || languages.contains(Language.JAVASCRIPT)) {
      extensions.addAll(List.of("ts", "tsx", "js", "jsx", "mjs", "cjs"));
    }
    return extensions;
  }

  /** Appends a section for the {@code group} extensions that the project has, if any. */
  private static void group(
      final StringBuilder body,
      final List<String> extensions,
      final Set<String> group,
      final String setting) {
    final List<String> present =
        extensions.stream().filter(group::contains).collect(Collectors.toList());
    if (present.isEmpty()) {
      return;
    }
    final String glob =
        present.size() == 1 ? "*." + present.getFirst() : "*.{" + String.join(",", present) + "}";
    body.append("\n[").append(glob).append("]\n").append(setting).append('\n');
  }
}
