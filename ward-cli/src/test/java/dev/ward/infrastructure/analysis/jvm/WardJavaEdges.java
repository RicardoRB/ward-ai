package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.application.rule.DependencyAnalysis;
import dev.ward.analysis.application.rule.FileSummaryCache;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.JavaDependencyAdapters;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** The edges of the ward graph that the Java adapters build for a project, as text. */
final class WardJavaEdges {
  private static final String ARROW = " -> ";

  private WardJavaEdges() {}

  /** Returns the {@code from -> to} edges of {@code project} with the optional classpath. */
  static Set<String> of(final Path project, final Optional<String> classpath) {
    final List<SourceUnit> units =
        new FileSystemSourceUnits().read(project, Set.of(AnalysisLanguage.JAVA)).stream().toList();
    final FrontendSupport frontend =
        new FrontendSupport(
            AnalysisLanguage.JAVA, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX));
    final List<DependencyEdge> edges =
        new DependencyAnalysis(
                List.of(frontend),
                new JavaDependencyAdapters().forProject(project.toAbsolutePath(), units, classpath))
            .analyze(units, NO_SUMMARIES)
            .graph()
            .edges();
    final Set<String> result = new TreeSet<>();
    for (final DependencyEdge edge : edges) {
      result.add(edge.from() + ARROW + edge.to());
    }
    return result;
  }

  private static final FileSummaryCache NO_SUMMARIES =
      new FileSummaryCache() {
        @Override
        public Optional<FileSummary> read(final SourceUnit unit) {
          return Optional.empty();
        }

        @Override
        public void write(final SourceUnit unit, final FileSummary summary) {}
      };
}
