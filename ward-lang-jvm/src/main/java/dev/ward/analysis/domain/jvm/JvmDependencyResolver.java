package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.port.DependencyResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the references of Java and Kotlin summaries into edges between project files. Both
 * languages index one set of declarations, so a file of either language can get an edge to a file
 * of the other, and each file is resolved with the lookup order of its own language. The source
 * roots decide which files each file can see: main sources see main sources, and test sources also
 * see the test sources of their own module.
 */
public final class JvmDependencyResolver implements DependencyResolver {
  private static final String PAIR_SEPARATOR = "\n";
  private static final String KOTLIN_SUFFIX = ".kt";
  private final List<JavaSourceRoot> roots;

  /**
   * Creates the resolver for the source roots of a project.
   *
   * @param roots the source roots of every module, in any order
   */
  public JvmDependencyResolver(final List<JavaSourceRoot> roots) {
    this.roots = List.copyOf(roots);
  }

  @Override
  public List<DependencyEdge> resolve(final List<FileSummary> summaries) {
    final JvmSourceIndex index = new JvmSourceIndex(summaries, roots);
    final Map<String, DependencyEdge> edges = new LinkedHashMap<>();
    summaries.stream()
        .flatMap(summary -> edgesOf(index, summary).stream())
        .forEach(edge -> edges.putIfAbsent(edge.from() + PAIR_SEPARATOR + edge.to(), edge));
    return new ArrayList<>(edges.values());
  }

  /** Picks the lookup order of the file's language, from its path. */
  private static List<DependencyEdge> edgesOf(
      final JvmSourceIndex index, final FileSummary summary) {
    if (summary.path().endsWith(KOTLIN_SUFFIX)) {
      return new KotlinFileResolution(index, summary).edges();
    }
    return new JavaFileResolution(index, summary).edges();
  }
}
