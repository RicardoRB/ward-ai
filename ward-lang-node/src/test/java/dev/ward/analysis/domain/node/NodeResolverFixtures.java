package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.node.NodeProjectLayout.ExportEntry;
import dev.ward.analysis.domain.node.NodeProjectLayout.NodePackage;
import dev.ward.analysis.domain.node.NodeProjectLayout.NodeTsConfig;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds layouts, summaries and edge labels for the resolver tests. */
final class NodeResolverFixtures {
  static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);

  private NodeResolverFixtures() {}

  static NodeProjectLayout layout(
      final List<String> files,
      final List<NodeTsConfig> configs,
      final List<NodePackage> packages) {
    return new NodeProjectLayout(Set.copyOf(files), configs, packages);
  }

  static NodePackage pkg(
      final String name, final String directory, final Map<String, List<ExportEntry>> exports) {
    return new NodePackage(name, directory, exports, null, null, null);
  }

  static FileSummary file(final String path, final String... imports) {
    final List<Reference> references =
        Arrays.stream(imports).map(value -> new Reference(value, SPAN)).toList();
    return new FileSummary(path, List.of(), references, List.of());
  }

  static List<String> edges(final NodeProjectLayout layout, final FileSummary... summaries) {
    return new NodeResolver(layout)
        .resolve(List.of(summaries)).stream().map(NodeResolverFixtures::label).toList();
  }

  static String label(final DependencyEdge edge) {
    return edge.from() + "->" + edge.to();
  }
}
