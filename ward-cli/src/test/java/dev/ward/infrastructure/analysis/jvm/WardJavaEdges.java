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
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** The edges of the ward graph that the JVM adapters build for a project, as text. */
final class WardJavaEdges {
  private static final String ARROW = " -> ";

  private WardJavaEdges() {}

  /** Returns the {@code from -> to} edges of {@code project} for Java sources and the classpath. */
  static Set<String> of(final Path project, final Optional<String> classpath) {
    return of(project, Set.of(AnalysisLanguage.JAVA), classpath);
  }

  /**
   * Returns the edges of {@code project} for the given JVM languages, with the optional classpath.
   */
  static Set<String> of(
      final Path project, final Set<AnalysisLanguage> languages, final Optional<String> classpath) {
    final List<SourceUnit> units =
        new FileSystemSourceUnits(new ProcessCommandExecutor())
            .read(project, languages).stream().toList();
    final List<FrontendSupport> frontends =
        languages.stream()
            .map(
                language ->
                    new FrontendSupport(
                        language, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX)))
            .toList();
    final List<DependencyEdge> edges =
        new DependencyAnalysis(
                frontends,
                new JvmDependencyAdapters().forProject(project.toAbsolutePath(), units, classpath))
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
