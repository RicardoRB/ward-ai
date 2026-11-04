package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.DependencyResolver;
import dev.ward.analysis.domain.port.DependencySupport;
import dev.ward.analysis.domain.port.FileSummarizer;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.RuleCapability;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyAnalysisSharedResolverTest {
  private static final Span AT_1 = new Span(1, 1, 1, 9, 0, 8);
  private static final SourceUnit JAVA_FILE = new SourceUnit("A.java", AnalysisLanguage.JAVA, "A");
  private static final SourceUnit KOTLIN_FILE =
      new SourceUnit("K.kt", AnalysisLanguage.KOTLIN, "K");
  private static final List<SourceUnit> UNITS = List.of(JAVA_FILE, KOTLIN_FILE);

  /** The paths each resolver was called with, one list per call. */
  private final List<List<String>> calls = new ArrayList<>();

  @Test
  void sharedResolverIsCalledOnceWithSummariesOfEveryLanguage() {
    final DependencyResolver shared = recording();
    final DependencySnapshot snapshot =
        analyze(support(AnalysisLanguage.JAVA, shared), support(AnalysisLanguage.KOTLIN, shared));

    assertThat(calls).hasSize(1);
    assertThat(calls.get(0)).containsExactlyInAnyOrder("A.java", "K.kt");
    assertThat(snapshot.graph().dependenciesOf("K.kt")).containsExactly("A.java");
  }

  @Test
  void registrationOrderDoesNotChangeTheGraph() {
    final DependencyResolver shared = recording();
    final DependencySnapshot javaFirst =
        analyze(support(AnalysisLanguage.JAVA, shared), support(AnalysisLanguage.KOTLIN, shared));
    final DependencySnapshot kotlinFirst =
        analyze(support(AnalysisLanguage.KOTLIN, shared), support(AnalysisLanguage.JAVA, shared));

    assertThat(kotlinFirst.graph().edges())
        .containsExactlyInAnyOrderElementsOf(javaFirst.graph().edges());
  }

  @Test
  void separateResolversEachReceiveOnlyTheirOwnLanguage() {
    analyze(
        support(AnalysisLanguage.JAVA, recording()), support(AnalysisLanguage.KOTLIN, recording()));

    assertThat(calls).hasSize(2);
    assertThat(calls.get(0)).containsExactly("A.java");
    assertThat(calls.get(1)).containsExactly("K.kt");
  }

  private DependencySnapshot analyze(final DependencySupport... supports) {
    return new DependencyAnalysis(frontends(), List.of(supports)).analyze(UNITS, new MemoryCache());
  }

  private DependencyResolver recording() {
    return summaries -> {
      final List<String> paths = new ArrayList<>();
      final List<DependencyEdge> edges = new ArrayList<>();
      for (final FileSummary summary : summaries) {
        paths.add(summary.path());
        if (!"A.java".equals(summary.path())) {
          edges.add(new DependencyEdge(summary.path(), "A.java", summary.imports().get(0)));
        }
      }
      calls.add(paths);
      return edges;
    };
  }

  private static DependencySupport support(
      final AnalysisLanguage language, final DependencyResolver resolver) {
    final FileSummarizer summarizer =
        (unit, tree) ->
            new FileSummary(unit.path(), List.of(), List.of(new Reference("A", AT_1)), List.of());
    return new DependencySupport(language, summarizer, resolver);
  }

  private static List<FrontendSupport> frontends() {
    final LanguageFrontend frontend =
        unit -> new ParseResult(new SyntaxNode("file", AT_1, Map.of(), List.of()), List.of());
    return List.of(
        new FrontendSupport(AnalysisLanguage.JAVA, frontend, Set.of(RuleCapability.SYNTAX)),
        new FrontendSupport(AnalysisLanguage.KOTLIN, frontend, Set.of(RuleCapability.SYNTAX)));
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
