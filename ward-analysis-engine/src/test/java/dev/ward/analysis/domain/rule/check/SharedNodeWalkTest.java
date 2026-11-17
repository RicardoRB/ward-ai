package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.NodeIndex;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.rule.UnitViews;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The forbidden-node rules of one file share a single walk of its tree. */
class SharedNodeWalkTest {
  private static final int RULES = 60;
  private final TreeLayout gen = new TreeLayout();

  private static ForbiddenNodeCheck check(final Set<String> kinds) {
    return new ForbiddenNodeCheck(NodeFilter.of(kinds), ForbiddenNodeCheck.Condition.ANY, "found");
  }

  @Test
  @DisplayName("one tree walk per file")
  void oneTreeWalkPerFile() {
    // Given a rule set with many forbidden-node rules
    final SyntaxNode loop = gen.node("loop", gen.leaf("'for'", "for"), gen.leaf("decl", "let"));
    final SyntaxNode root = gen.node(
        "unit",
        gen.leaf("decl", "let"),
        loop,
        gen.leaf("decl", "let")
    );
    final List<Set<String>> wanted = List.of(
        Set.of("decl"),
        Set.of("loop"),
        Set.of("decl", "loop")
    );
    final List<ForbiddenNodeCheck> checks =
        IntStream.range(0, RULES).mapToObj(i -> check(wanted.get(i % wanted.size()))).toList();
    final Set<String> kinds = Set.of("decl", "loop");
    final NodeIndex index = new NodeIndex(root, kinds);
    final UnitViews views =
        new UnitViews(FlowView.NONE, ScopeView.NONE, UnitTypeFacts.none(), index);
    final SourceUnit unit = new SourceUnit("A.java", AnalysisLanguage.JAVA, "class A {}");

    // When the ward gate analyzes the file
    final List<Object> shared = new ArrayList<>();
    checks.forEach(c -> shared.add(c.matchesIn(root, views, unit)));

    // Then the tree is walked once, and each node goes to the rules that want its kind
    assertThat(index.walks()).isEqualTo(1);
    assertThat(checks.get(0).nodeKinds()).containsExactly("decl");
    for (int i = 0; i < RULES; i++) {
      assertThat(shared.get(i)).isEqualTo(checks.get(i).matches(root));
    }
  }
}
