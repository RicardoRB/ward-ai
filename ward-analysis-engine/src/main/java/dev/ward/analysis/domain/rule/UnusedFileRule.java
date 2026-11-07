package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.graph.ProjectFiles;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The {@code ward:architecture/unused-file} rule: reports each analysed file that no other file
 * imports, unless it is an entry point declared by a manifest or matches one of the entry globs
 * given as options. The rule reads the whole graph once, so it is a graph rule.
 */
public final class UnusedFileRule implements GraphRule {

  /** The id of the rule, the same in every language. */
  public static final String ID = "ward:architecture/unused-file";

  private static final String MESSAGE = "file is not imported by any other file";
  private static final List<String> SOURCE_SUFFIXES =
      List.of(".ts", ".tsx", ".mts", ".cts", ".js", ".jsx", ".mjs", ".cjs");

  private final RuleDefinition definition;
  private final List<PathMatcher> entryGlobs;

  /**
   * Creates the rule with the entry globs of {@code entries}, project-relative patterns such as
   * {@code src/**}{@code /*.test.ts}.
   *
   * @throws NullPointerException if a field is missing
   */
  public UnusedFileRule(final RuleDefinition definition, final List<String> entries) {
    this.definition = Objects.requireNonNull(definition, "definition");
    this.entryGlobs =
        Objects.requireNonNull(entries, "entries").stream()
            .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob))
            .toList();
  }

  @Override
  public RuleDefinition definition() {
    return definition;
  }

  /** Without a file list nothing can be unused, so this reports nothing. */
  @Override
  public List<GraphMatch> matches(final DependencyGraph graph) {
    return matches(graph, ProjectFiles.EMPTY);
  }

  @Override
  public List<GraphMatch> matches(final DependencyGraph graph, final ProjectFiles project) {
    final List<GraphMatch> matches = new ArrayList<>();
    for (final String file : new TreeSet<>(project.files())) {
      if (!isSource(file) || isUsed(graph, project, file)) {
        continue;
      }
      matches.add(new GraphMatch(file, new RuleMatch(null, MESSAGE)));
    }
    return List.copyOf(matches);
  }

  private boolean isUsed(
      final DependencyGraph graph, final ProjectFiles project, final String file) {
    return project.entries().contains(file)
        || matchesEntryGlob(file)
        || !graph.dependentsOf(file).isEmpty();
  }

  /** Only TypeScript and JavaScript files are reported; other analysed files are not. */
  private static boolean isSource(final String file) {
    return SOURCE_SUFFIXES.stream().anyMatch(file::endsWith);
  }

  private boolean matchesEntryGlob(final String file) {
    final Path path = Path.of(file);
    return entryGlobs.stream().anyMatch(glob -> glob.matches(path));
  }
}
