package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.port.DependencyResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the references of Java summaries into edges between project files, by Java name lookup.
 * The source roots decide which files each file can see: main sources see main sources, and test
 * sources also see the test sources of their own module.
 */
public final class JavaDependencyResolver implements DependencyResolver {
  private static final String PAIR_SEPARATOR = "\n";
  private final List<JavaSourceRoot> roots;

  /**
   * Creates the resolver for the source roots of a project.
   *
   * @param roots the source roots of every module, in any order
   */
  public JavaDependencyResolver(final List<JavaSourceRoot> roots) {
    this.roots = List.copyOf(roots);
  }

  @Override
  public List<DependencyEdge> resolve(final List<FileSummary> summaries) {
    final JavaSourceIndex index = new JavaSourceIndex(summaries, roots);
    final Map<String, DependencyEdge> edges = new LinkedHashMap<>();
    summaries.stream()
        .flatMap(summary -> new JavaFileResolution(index, summary).edges().stream())
        .forEach(edge -> edges.putIfAbsent(edge.from() + PAIR_SEPARATOR + edge.to(), edge));
    return new ArrayList<>(edges.values());
  }
}
