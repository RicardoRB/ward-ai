package dev.ward.analysis.domain.scope;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachedScopesTest {

  private static final SyntaxNode TREE =
      new SyntaxNode("root", new Span(1, 1, 1, 1, 0, 0), Map.of(), List.of());

  private static final class Counting implements ScopeAnalysis {
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
      calls.incrementAndGet();
      return UnitScopes.EMPTY;
    }

    @Override
    public Set<String> builtins(final Set<String> environments) {
      return environments;
    }
  }

  @Test
  void anUnchangedUnitIsAnalyzedOnceWhateverTheNumberOfReaders() {
    final Counting delegate = new Counting();
    final CachedScopes cached = new CachedScopes(delegate);
    final SourceUnit unit = new SourceUnit("a.py", AnalysisLanguage.PYTHON, "x = 1\n");

    final UnitScopes first = cached.analyze(unit, TREE);
    final UnitScopes second =
        cached.analyze(new SourceUnit("a.py", AnalysisLanguage.PYTHON, "x = 1\n"), TREE);

    assertThat(second).isSameAs(first);
    assertThat(delegate.calls).hasValue(1);
  }

  @Test
  void aChangedUnitOrAnotherPathIsAnalyzedAgain() {
    final Counting delegate = new Counting();
    final CachedScopes cached = new CachedScopes(delegate);

    cached.analyze(new SourceUnit("a.py", AnalysisLanguage.PYTHON, "x = 1\n"), TREE);
    cached.analyze(new SourceUnit("a.py", AnalysisLanguage.PYTHON, "x = 2\n"), TREE);
    cached.analyze(new SourceUnit("b.py", AnalysisLanguage.PYTHON, "x = 1\n"), TREE);

    assertThat(delegate.calls).hasValue(3);
  }

  @Test
  void builtinsComeFromTheDelegate() {
    assertThat(new CachedScopes(new Counting()).builtins(Set.of("node"))).containsExactly("node");
  }
}
