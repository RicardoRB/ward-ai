package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.DependencyResolver;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.port.FileSummarizer;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.DependencyView;
import dev.ward.analysis.domain.rule.RuleCapability;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyAnalysisTest {
  private static final Span AT_1 = new Span(1, 1, 1, 9, 0, 8);
  private static final SourceUnit A = new SourceUnit("A.java", AnalysisLanguage.JAVA, "A");
  private static final SourceUnit B = new SourceUnit("B.java", AnalysisLanguage.JAVA, "B");

  private final List<String> summarized = new ArrayList<>();

  @Test
  void resolvesEdgesFromSummariesOfTheLanguage() {
    final DependencySnapshot snapshot =
        new DependencyAnalysis(frontends(), List.of(support(AnalysisLanguage.JAVA, edgeFromB())))
            .analyze(List.of(A, B), new MemoryCache());

    assertThat(snapshot.graph().dependenciesOf("B.java")).containsExactly("A.java");
    assertThat(snapshot.summaries()).containsOnlyKeys("A.java", "B.java");
    assertThat(snapshot.view("A.java").dependents())
        .extracting(FileSummary::path)
        .containsExactly("B.java");
  }

  @Test
  void languageWithoutSummarizerContributesNoSummariesOrEdges() {
    final DependencySnapshot snapshot =
        new DependencyAnalysis(frontends(), List.of()).analyze(List.of(A, B), new MemoryCache());

    assertThat(snapshot.summaries()).isEmpty();
    assertThat(snapshot.graph().edges()).isEmpty();
    assertThat(summarized).isEmpty();
    assertThat(snapshot.view("A.java")).isEqualTo(DependencyView.NONE);
  }

  @Test
  void storedSummaryIsReusedWithoutSummarizingAgain() {
    final MemoryCache cache = new MemoryCache();
    final DependencyAnalysis analysis =
        new DependencyAnalysis(frontends(), List.of(support(AnalysisLanguage.JAVA, edgeFromB())));
    final DependencySnapshot first = analysis.analyze(List.of(A, B), cache);
    summarized.clear();

    final DependencySnapshot second = analysis.analyze(List.of(A, B), cache);

    assertThat(summarized).isEmpty();
    assertThat(second).isEqualTo(first);
  }

  @Test
  void unitWithSyntaxErrorHasNoSummary() {
    final SourceUnit broken = new SourceUnit("Broken.java", AnalysisLanguage.JAVA, "broken");
    final LanguageFrontend frontend =
        unit ->
            new ParseResult(
                root(),
                List.of(
                    new AnalysisFinding(
                        "ward:java/syntax",
                        AnalysisSeverity.ERROR,
                        "bad",
                        null,
                        null,
                        null,
                        null)));
    final DependencySnapshot snapshot =
        new DependencyAnalysis(
                List.of(
                    new FrontendSupport(
                        AnalysisLanguage.JAVA, frontend, Set.of(RuleCapability.SYNTAX))),
                List.of(support(AnalysisLanguage.JAVA, edgeFromB())))
            .analyze(List.of(broken), new MemoryCache());

    assertThat(snapshot.summaries()).isEmpty();
  }

  private List<FrontendSupport> frontends() {
    final LanguageFrontend frontend = unit -> new ParseResult(root(), List.of());
    return List.of(
        new FrontendSupport(AnalysisLanguage.JAVA, frontend, Set.of(RuleCapability.SYNTAX)));
  }

  private DependencySupport support(
      final AnalysisLanguage language, final DependencyResolver resolver) {
    final FileSummarizer summarizer =
        (unit, tree) -> {
          summarized.add(unit.path());
          return new FileSummary(
              unit.path(),
              List.of(new Symbol(unit.text(), AT_1)),
              "B".equals(unit.text()) ? List.of(new Reference("A", AT_1)) : List.of(),
              List.of());
        };
    return new DependencySupport(language, summarizer, resolver);
  }

  private static DependencyResolver edgeFromB() {
    return summaries -> {
      final List<DependencyEdge> edges = new ArrayList<>();
      for (final FileSummary summary : summaries) {
        for (final Reference reference : summary.imports()) {
          edges.add(new DependencyEdge(summary.path(), "A.java", reference));
        }
      }
      return edges;
    };
  }

  private static SyntaxNode root() {
    return new SyntaxNode("file", AT_1, Map.of(), List.of());
  }

  /** Keeps summaries in memory, as the gate keeps them on disk. */
  private static final class MemoryCache implements FileSummaryCache {
    private final Map<String, FileSummary> stored = new HashMap<>();

    @Override
    public Optional<FileSummary> read(final SourceUnit unit) {
      return Optional.ofNullable(stored.get(unit.path()));
    }

    @Override
    public void write(final SourceUnit unit, final FileSummary summary) {
      stored.put(unit.path(), summary);
    }
  }
}
