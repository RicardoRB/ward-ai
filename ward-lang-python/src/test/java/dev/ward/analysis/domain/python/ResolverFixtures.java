package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.List;

/** Builds hand-made file summaries and runs the Python resolver on them, for resolver tests. */
final class ResolverFixtures {

  static final Span SPAN = new Span(1, 1, 1, 1, 0, 0);

  private ResolverFixtures() {}

  /** A summary of {@code path} whose imports are the given specifiers. */
  static FileSummary file(final String path, final String... specifiers) {
    final List<Reference> imports = new ArrayList<>();
    for (final String specifier : specifiers) {
      imports.add(new Reference(specifier, SPAN));
    }
    return new FileSummary(path, List.of(), imports, List.of());
  }

  static List<DependencyEdge> resolve(
      final PythonProjectLayout layout, final FileSummary... summaries) {
    return new PythonDependencyResolver(layout).resolve(List.of(summaries));
  }

  static List<String> targets(final List<DependencyEdge> edges) {
    return edges.stream().map(DependencyEdge::to).toList();
  }

  static List<String> edgeKeys(final List<DependencyEdge> edges) {
    return edges.stream().map(edge -> edge.from() + " -> " + edge.to()).toList();
  }
}
