package dev.ward.analysis.domain.node.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks that expressions the grammar reads as a name and an operand get their operators back. */
class ReassociateTest {

  /** Renders the expression of the first {@code const} declaration with its grouping visible. */
  private static String grouped(final String initializer) {
    final String text = "const x = " + initializer + ";\n";
    final SyntaxNode tree =
        Reassociate.of(
            new AntlrLanguageFrontend()
                .parse(new SourceUnit("a.ts", AnalysisLanguage.TYPESCRIPT, text))
                .tree());
    final SyntaxNode declaration = TreeKit.find(tree, "variableDeclaration");
    final List<SyntaxNode> values = TreeKit.children(declaration, "singleExpression");
    return render(values.get(values.size() - 1));
  }

  private static String render(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    if (!"singleExpression".equals(node.kind()) || parts.size() <= 1) {
      return TreeText.text(node);
    }
    final StringBuilder out = new StringBuilder("(");
    for (final SyntaxNode part : parts) {
      out.append("arguments".equals(part.kind()) ? "args" : render(part)).append(' ');
    }
    return out.toString().trim() + ")";
  }

  @Test
  void callsBecomeACalleeAndArguments() {
    assertThat(grouped("f(1)")).isEqualTo("(f args)");
    assertThat(grouped("load(1).then(g)")).isEqualTo("(((load args) . then) args)");
    assertThat(grouped("f(1)(2)")).isEqualTo("((f args) args)");
  }

  @Test
  void binaryOperatorsFollowPrecedence() {
    assertThat(grouped("a + b * c")).isEqualTo("(a + (b * c))");
    assertThat(grouped("a * b + c")).isEqualTo("((a * b) + c)");
    assertThat(grouped("a - b - c")).isEqualTo("((a - b) - c)");
    assertThat(grouped("a + b == c")).isEqualTo("((a + b) == c)");
    assertThat(grouped("a + f(b).c")).isEqualTo("(a + ((f args) . c))");
  }

  @Test
  void indexingAndMembersChainFromTheName() {
    assertThat(grouped("names[0]")).isEqualTo("(names [ 0 ])");
    assertThat(grouped("names[0].length")).isEqualTo("((names [ 0 ]) . length)");
  }

  @Test
  void expressionsWithoutAnOperandAfterTheNameAreLeftAlone() {
    assertThat(grouped("a")).isEqualTo("a");
    assertThat(grouped("a.b")).isEqualTo("(a . b)");
  }
}
