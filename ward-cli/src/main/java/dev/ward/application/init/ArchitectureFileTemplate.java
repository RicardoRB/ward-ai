package dev.ward.application.init;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.init.ArchitectureLayers;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes the {@code ward-architecture.toml} of a chosen architecture style, but only for a project
 * that has none: once the file exists it is the user's, and init never writes it again. The file
 * starts with one rule that the {@code layers} table of {@code ward.toml} does not state for the
 * style, and an example of the {@code external} selector.
 */
final class ArchitectureFileTemplate {
  static final String FILE_NAME = "ward-architecture.toml";
  static final String BEGIN =
      "#:schema https://ricardorb.github.io/ward-ai/schemas/ward-architecture.schema.json";
  static final String END = "# because = \"the domain must not know the framework\"";
  private static final String TESTS =
      "[\"src/test/**\", \"**/src/test/**\", \"Tests/**\", \"**/Tests/**\"]";
  private static final String HEADER =
      """
      # Architecture rules of this project, checked by the ward gate. `ward init` wrote this file
      # once for the %s style; it is yours now, and init never changes it again. The boundary of
      # the style itself is in [[checks.ward.layers]] of ward.toml.
      version = 1

      """;
  private static final String RULE =
      """
      [[rule]]
      name = "%s"
      select = { directories = [%s], exclude-paths = %s }
      must-not-depend-on = { directories = [%s] }
      because = "%s"
      allow-empty = true

      """;
  private static final String EXTERNAL_EXAMPLE =
      """
      # `external` names code outside the project by the package or module it is imported with.
      # Remove the `# ` to use it, with your own names:
      # [[rule]]
      # name = "domain-is-framework-free"
      # select = { directories = ["domain"] }
      # must-not-depend-on = { external = ["org.springframework", "express", "django"] }
      """;

  private ArchitectureFileTemplate() {}

  /** Returns the project's {@code ward-architecture.toml}, which may not exist. */
  static Path path(final Project project) {
    return project.root().resolve(FILE_NAME);
  }

  /**
   * Returns the section that writes the file, when the user chose the style and the file does not
   * exist. Every init writes the {@code ward} gate that reads it. The file is written whole, so a
   * file that appears before the write blocks it.
   */
  static Optional<OwnedSection> section(
      final Project project,
      final InitSelection selection,
      final boolean exists
  ) {
    if (exists || !selection.architectureChosen()) {
      return Optional.empty();
    }
    return Optional.of(new OwnedSection(path(project), BEGIN, END, body(project, selection), true));
  }

  /** Returns the text between the first and the last line of the file. */
  static String body(final Project project, final InitSelection selection) {
    final String style = WardArchitecture.style(selection);
    return HEADER.formatted(style)
        + rule(style, ArchitectureLayers.forStyle(style, project.framework()))
        + EXTERNAL_EXAMPLE;
  }

  /** The rule of {@code style} that points inward, where its boundary points outward. */
  private static String rule(final String style, final LayerBoundary boundary) {
    return switch (style) {
      case "hexagonal" ->
          rule(
              "domain-is-innermost",
              List.of("domain"),
              List.of("application"),
              "the application calls the domain, and the domain calls nothing above it"
          );
      case "clean" ->
          rule(
              "entities-do-not-know-use-cases",
              boundary.from(),
              List.of("usecase", "usecases", "application"),
              "use cases call the entities, not the reverse"
          );
      default ->
          rule(
              "lower-layers-do-not-call-controllers",
              Stream.concat(Stream.of("service"), boundary.forbidden().stream()).toList(),
              boundary.from(),
              "a controller calls the layers below it, not the reverse"
          );
    };
  }

  private static String rule(
      final String name,
      final List<String> select,
      final List<String> forbidden,
      final String because
  ) {
    return RULE.formatted(name, quoted(select), TESTS, quoted(forbidden), because);
  }

  private static String quoted(final List<String> names) {
    return names.stream().map(name -> '"' + name + '"').collect(Collectors.joining(", "));
  }
}
