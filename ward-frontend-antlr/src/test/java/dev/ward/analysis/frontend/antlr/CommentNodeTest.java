package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommentNodeTest {

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
    assertThat(text(unit.children().get(comment))).isEqualTo("/** Documents the class. */");
  }

  @Test
  void lineAndBlockCommentsKeepTheirTextAndPosition() {
    final String text = "// first\n/* second\n   line */\nclass A {}\n";

    final List<SyntaxNode> comments = commentNodes(parse(text).tree());

    assertThat(comments).hasSize(2);
    assertThat(text(comments.get(0))).isEqualTo("// first");
    assertThat(comments.get(0).span().startLine()).isEqualTo(1);
    assertThat(text(comments.get(1))).isEqualTo("/* second\n   line */");
    assertThat(comments.get(1).span().startLine()).isEqualTo(2);
    assertThat(comments.get(1).span().endLine()).isEqualTo(3);
    assertThat(comments.get(1).span().endColumn()).isEqualTo(10);
  }

  @Test
  void commentOnlyFileHasCommentNodesAndNoDiagnostics() {
    final ParseResult result = parse("// nothing here\n/* still nothing */\n");

    assertThat(commentNodes(result.tree())).hasSize(2);
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  void brokenFileKeepsCommentNodeAndSyntaxError() {
    final ParseResult result = parse("/** Broken. */\nclass Broken { int = ; }\n");

    assertThat(commentNodes(result.tree())).hasSize(1);
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
    final String withoutComments =
        """
        package dev.example;

        public class Order {
          int count;
        }
        """;

    assertThat(shape(withoutCommentNodes(parse(withComments).tree())))
        .isEqualTo(shape(parse(withoutComments).tree()));
  }

  private static ParseResult parse(final String text) {
    return new AntlrLanguageFrontend()
        .parse(new SourceUnit("Order.java", AnalysisLanguage.JAVA, text));
  }

  private static int indexOfKind(final List<SyntaxNode> nodes, final String kind) {
    for (int i = 0; i < nodes.size(); i++) {
      if (nodes.get(i).kind().equals(kind)) {
        return i;
      }
    }
    return -1;
  }

  private static String text(final SyntaxNode node) {
    return node.fields().get("text");
  }

  private static List<SyntaxNode> commentNodes(final SyntaxNode root) {
    final List<SyntaxNode> comments = new ArrayList<>();
    collectComments(root, comments);
    return comments;
  }

  private static void collectComments(final SyntaxNode node, final List<SyntaxNode> comments) {
    if (node.kind().equals(SyntaxTreeMapper.COMMENT_KIND)) {
      comments.add(node);
    }
    node.children().forEach(child -> collectComments(child, comments));
  }

  private static SyntaxNode withoutCommentNodes(final SyntaxNode node) {
    final List<SyntaxNode> children =
        node.children().stream()
            .filter(child -> !child.kind().equals(SyntaxTreeMapper.COMMENT_KIND))
            .map(CommentNodeTest::withoutCommentNodes)
            .toList();
    return new SyntaxNode(node.kind(), node.span(), node.fields(), children);
  }

  /** Kinds, texts and nesting only: comments move later lines, so spans are not compared. */
  private static String shape(final SyntaxNode node) {
    final StringBuilder out = new StringBuilder();
    appendShape(node, out);
    return out.toString();
  }

  private static void appendShape(final SyntaxNode node, final StringBuilder out) {
    out.append(node.kind()).append(node.fields()).append('[');
    node.children().forEach(child -> appendShape(child, out));
    out.append(']');
  }
}
