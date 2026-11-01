package dev.ward.application.init.swift;

import dev.ward.application.init.PartialCheckDefaults.Table;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/** The partial-run tables of the SwiftPM and Xcode gates that can be scoped to edited files. */
final class SwiftPartialTables {
  private static final List<String> PATHS = List.of("{paths}");
  private static final List<String> SWIFT_SOURCES = List.of("**/*.swift");

  // Both commands take the edited files as arguments, so the narrowed run checks only those.
  // SwiftLint takes the gate's own command, so a strict edit hook lints as strictly as pre-push.
  private static final Map<String, BiFunction<Project, List<String>, Table>> TABLES =
      Map.of(
          "swift-format",
          (project, command) ->
              new Table(
                  SWIFT_SOURCES,
                  List.of(".swift-format"),
                  PATHS,
                  null,
                  null,
                  List.of("swift", "format", "lint", "--strict")),
          "swiftlint",
          (project, command) ->
              new Table(
                  SWIFT_SOURCES,
                  List.of(".swiftlint.yml", SwiftTools.STRICT_CONFIG),
                  PATHS,
                  null,
                  null,
                  command));

  /** The gates that have a partial table. */
  static final Set<String> GATES = TABLES.keySet();

  private SwiftPartialTables() {}

  /** Returns the partial-run table of a Swift gate, or empty for any other gate. */
  static Optional<Table> forGate(
      final String gate, final Project project, final List<String> command) {
    return Optional.ofNullable(TABLES.get(gate)).map(factory -> factory.apply(project, command));
  }
}
