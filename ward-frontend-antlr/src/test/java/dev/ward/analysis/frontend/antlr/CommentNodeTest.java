package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommentNodeTest {

  private static final int BLOCK_END_LINE = 3;
  private static final int BLOCK_END_COLUMN = 10;

  private static final String JAVADOC_CLASS =
      """
      package dev.example;

      /** Documents the class. */
      public class Order {
      }
      """;

  @Test
  void javadocBeforeClassBecomesSiblingBeforeTheTypeDeclaration() {
    final SyntaxNode unit = parse(JAVADOC_CLASS).tree();

    final int comment = indexOfKind(unit.children(), SyntaxTreeMapper.COMMENT_KIND);
    final int declaration = indexOfKind(unit.children(), "typeDeclaration");

    assertThat(comment).isNotNegative();
    assertThat(comment + 1).isEqualTo(declaration);
    assertThat(SyntaxTrees.text(unit.children().get(comment)))
        .isEqualTo("/** Documents the class. */");
  }

  @Test
  void lineAndBlockCommentsKeepTheirTextAndPosition() {
    final String source = "// first\n/* second\n   line */\nclass A {}\n";

    final List<SyntaxNode> found = SyntaxTrees.comments(parse(source).tree());

    assertThat(found).hasSize(2);
    assertThat(SyntaxTrees.text(found.get(0))).isEqualTo("// first");
    assertThat(found.get(0).span().startLine()).isEqualTo(1);
    assertThat(SyntaxTrees.text(found.get(1))).isEqualTo("/* second\n   line */");
    assertThat(found.get(1).span().startLine()).isEqualTo(2);
    assertThat(found.get(1).span().endLine()).isEqualTo(BLOCK_END_LINE);
    assertThat(found.get(1).span().endColumn()).isEqualTo(BLOCK_END_COLUMN);
  }

  @Test
  void commentOnlyFileHasCommentNodesAndNoDiagnostics() {
    final ParseResult result = parse("// nothing here\n/* still nothing */\n");

    assertThat(SyntaxTrees.comments(result.tree())).hasSize(2);
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  void brokenFileKeepsCommentNodeAndSyntaxError() {
    final ParseResult result = parse("/** Broken. */\nclass Broken { int = ; }\n");

    assertThat(SyntaxTrees.comments(result.tree())).hasSize(1);
    assertThat(result.diagnostics()).isNotEmpty();
  }

  @Test
  void nonCommentNodesAreUnchangedByComments() {
    final String withComments =
        """
        // header
        package dev.example;

        /** Documents the class. */
        public class Order {
          /* field */ int count;
        }
        """;
    final String plain =
        """
        package dev.example;

        public class Order {
          int count;
        }
        """;

    assertThat(SyntaxTrees.shape(SyntaxTrees.withoutComments(parse(withComments).tree())))
        .isEqualTo(SyntaxTrees.shape(parse(plain).tree()));
  }

  private static ParseResult parse(final String text) {
    return new AntlrLanguageFrontend()
        .parse(new SourceUnit("Order.java", AnalysisLanguage.JAVA, text));
  }

  private static int indexOfKind(final List<SyntaxNode> nodes, final String kind) {
    for (int i = 0; i < nodes.size(); i++) {
      if (kind.equals(nodes.get(i).kind())) {
        return i;
      }
    }
    return -1;
  }
}
