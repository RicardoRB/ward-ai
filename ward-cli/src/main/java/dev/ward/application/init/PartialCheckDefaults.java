package dev.ward.application.init;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code [checks.<id>.paths]} table init writes for a gate whose tool can be scoped to edited
 * files. Each toolchain owns the tables of its own gates through its {@link ToolchainInitSupport};
 * this class keeps the tables of the gates every project runs the same way. Each narrowing form was
 * verified against the real tool: a form the tool does not understand analyzes nothing and passes.
 */
public final class PartialCheckDefaults {
  private static final List<String> WHOLE_PROJECT = List.of("**");
  private static final List<String> PATHS = List.of("{paths}");

  private static final Map<String, Table> NEUTRAL_TABLES =
      Map.of(
          "semgrep",
          new Table(WHOLE_PROJECT, List.of(".semgrep*"), PATHS, null, null),
          // Its history scan misses uncommitted and staged edits, so edited files are scanned
          // directly.
          "gitleaks",
          new Table(
              WHOLE_PROJECT,
              List.of(".gitleaks*"),
              List.of("--no-git", "--source={paths}"),
              null,
              null));

  /**
   * The table's keys; {@code item} and {@code separator} are omitted when {@code null}, and {@code
   * command} when empty.
   */
  public record Table(
      List<String> match,
      List<String> full,
      List<String> args,
      String item,
      String separator,
      List<String> command) {
    /** Copies the lists, so the table keeps none that its caller can still change. */
    public Table {
      match = List.copyOf(match);
      full = List.copyOf(full);
      args = List.copyOf(args);
      command = List.copyOf(command);
    }

    /** Creates a table that runs no command of its own. */
    public Table(
        final List<String> match,
        final List<String> full,
        final List<String> args,
        final String item,
        final String separator) {
      this(match, full, args, item, separator, List.of());
    }

    /**
     * Returns a table that runs {@code full} and the whole {@code match} set, with no arguments.
     */
    public static Table whole(final List<String> match, final List<String> full) {
      return new Table(match, full, List.of(), null, null);
    }
  }

  private PartialCheckDefaults() {}

  /** Returns the partial-run table of {@code gate}; {@code command} is the gate's full command. */
  public static Optional<Table> forGate(
      final String gate,
      final Project project,
      final QualityPreset preset,
      final List<String> command) {
    return InitToolchains.find(project)
        .flatMap(support -> support.partialTable(gate, project, preset, command))
        .or(() -> Optional.ofNullable(NEUTRAL_TABLES.get(gate)));
  }

  /** Returns the gates that have a partial table, for the gate contract test. */
  static Set<String> gates() {
    final Set<String> gates = new HashSet<>(NEUTRAL_TABLES.keySet());
    InitToolchains.all().forEach(support -> gates.addAll(support.partialGates()));
    return gates;
  }

  /**
   * The OpenAPI lint and diff gates always analyze the whole spec, so editing it runs them in full
   * and other edits skip them.
   */
  static Optional<Table> forOpenApi(final String gate, final String spec) {
    if (spec == null
        || !SupportedQualityGates.OPENAPI_LINT.equals(gate)
            && !SupportedQualityGates.OPENAPI_DIFF.equals(gate)) {
      return Optional.empty();
    }
    return Optional.of(Table.whole(List.of(spec), List.of()));
  }
}
