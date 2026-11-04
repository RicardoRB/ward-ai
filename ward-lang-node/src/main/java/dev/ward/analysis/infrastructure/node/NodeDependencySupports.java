package dev.ward.analysis.infrastructure.node;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.node.NodeFileSummarizer;
import dev.ward.analysis.domain.node.NodeProjectLayout;
import dev.ward.analysis.domain.node.NodeResolver;
import dev.ward.analysis.domain.port.DependencySupport;
import java.util.List;

/**
 * The dependency adapters of the TypeScript and JavaScript languages for one project. Both share
 * one resolver over the project's layout, so a TypeScript file can reach a JavaScript file.
 */
public final class NodeDependencySupports {

  private NodeDependencySupports() {}

  /** Returns the TypeScript and JavaScript supports that resolve against {@code layout}. */
  public static List<DependencySupport> create(final NodeProjectLayout layout) {
    final NodeFileSummarizer summarizer = new NodeFileSummarizer();
    final NodeResolver resolver = new NodeResolver(layout);
    return List.of(
        new DependencySupport(AnalysisLanguage.TYPESCRIPT, summarizer, resolver),
        new DependencySupport(AnalysisLanguage.JAVASCRIPT, summarizer, resolver));
  }
}
