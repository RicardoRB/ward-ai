package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.DependencyEdge;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code ward:python/cyclic-import} rule, as Pylint's cyclic-import: each strongly connected
 * group of Python modules is reported once, as its shortest cycle, at the first import of its
 * alphabetically first module. Pylint can report several cycles inside one such group; this rule
 * reports one per group. Each module is its own group, and an import under a {@code TYPE_CHECKING}
 * guard is not an edge.
 *
 * <p>shortcut: a module is labelled by its file path with {@code /} for {@code .} and no {@code
 * .py}, not its dotted name, so a module under a {@code src/} root reads {@code src.pkg.mod};
 * upgrade by naming modules from the project's source roots.
 */
public final class CyclicImportRule implements GraphRule {

  /** The id of the rule. */
  public static final String ID = "ward:python/cyclic-import";

  private static final String PYTHON_SUFFIX = ".py";
  private static final String SEPARATOR = " -> ";

  private final RuleDefinition definition;

  /**
   * Creates the rule described by {@code definition}.
   *
   * @throws NullPointerException if the definition is missing
   */
  public CyclicImportRule(final RuleDefinition definition) {
    this.definition = Objects.requireNonNull(definition, "definition");
  }

  @Override
  public RuleDefinition definition() {
    return definition;
  }

  @Override
  public List<GraphMatch> matches(final DependencyGraph graph) {
    final List<DependencyEdge> edges = new ArrayList<>();
    for (final DependencyEdge edge : graph.edges()) {
      if (isRuntimePythonEdge(edge)) {
        edges.add(edge);
      }
    }
    final List<GraphMatch> matches = new ArrayList<>();
    for (final CycleSearch.Cycle cycle : CycleSearch.find(edges, Optional::of)) {
      matches.add(cycleMatch(cycle));
    }
    return List.copyOf(matches);
  }

  /**
   * Whether {@code edge} joins two Python modules at run time. The graph holds every language's
   * edges, so a Java or TypeScript cycle must not reach this rule.
   */
  private static boolean isRuntimePythonEdge(final DependencyEdge edge) {
    return !edge.reference().typeChecking()
        && isPythonModule(edge.from())
        && isPythonModule(edge.to());
  }

  private static boolean isPythonModule(final String path) {
    return path.endsWith(PYTHON_SUFFIX);
  }

  private static GraphMatch cycleMatch(final CycleSearch.Cycle cycle) {
    final List<String> modules = new ArrayList<>();
    for (final String path : cycle.slices().subList(0, cycle.slices().size() - 1)) {
      modules.add(label(path));
    }
    final DependencyEdge first = cycle.edges().getFirst();
    return new GraphMatch(
        first.from(),
        new RuleMatch(
            first.reference().span(), "Cyclic import (" + String.join(SEPARATOR, modules) + ")"));
  }

  private static String label(final String path) {
    final String module =
        path.endsWith(PYTHON_SUFFIX)
            ? path.substring(0, path.length() - PYTHON_SUFFIX.length())
            : path;
    return module.replace('/', '.');
  }
}
