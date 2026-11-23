package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeLinesTest {
  private static final String TEXT = "text";
  private static final String UNIT = "unit";

  private static SyntaxNode leaf(
      final String kind,
      final String text,
      final int line,
      final int column
  ) {
    return new SyntaxNode(
        kind,
        new Span(line, column, line, column + text.length(), 0, 0),
        Map.of(TEXT, text),
        List.of()
    );
  }

  private static SyntaxNode unit(final SyntaxNode... leaves) {
    return new SyntaxNode(UNIT, new Span(1, 1, 1, 1, 0, 0), Map.of(), List.of(leaves));
  }

  @Test
  void aStringOnOneLineIsBlankedAndTheCodeAroundItStays() {
    final SourceFile file = new SourceFile("a.swift", "let a = \"x; y\"; run()");
    final SyntaxNode root = unit(leaf("tok", "let", 1, 1), leaf("string", "\"x; y\"", 1, 9));

    assertThat(CodeLines.of(root, file)).containsExactly("let a =       ; run()");
  }

  @Test
  void everyLineOfAStringOverSeveralLinesIsBlanked() {
    final String literal = "\"\"\"\n  {\n  \"\"\"";
    final SourceFile file = new SourceFile("a.swift", "let a = " + literal + "\nrun()");
    final SyntaxNode root = unit(leaf("string", literal, 1, 9));

    assertThat(CodeLines.of(root, file)).containsExactly("let a =    ", "   ", "     ", "run()");
  }

  @Test
  void aCommentIsBlankedAndALineBeyondTheFileIsIgnored() {
    final SourceFile file = new SourceFile("a.swift", "run() // a; b");
    final SyntaxNode root =
        unit(leaf(SyntaxWalk.COMMENT_KIND, "// a; b", 1, 7), leaf("string", "'c'", 2, 1));

    assertThat(CodeLines.of(root, file)).containsExactly("run()        ");
  }

  @Test
  void aSectionSkippedByAFalseConditionalBranchIsBlankedOnEveryLine() {
    final SourceFile file = new SourceFile("a.cs", "#if X\n  a;\n b;\n#endif\nrun();");
    final SyntaxNode skipped =
        new SyntaxNode(
            "skipped",
            new Span(2, 1, 4, 1, 0, 0),
            Map.of("skipped", "  a;\n b;\n"),
            List.of()
        );

    assertThat(CodeLines.of(unit(skipped), file))
        .containsExactly("#if X", "    ", "   ", "#endif", "run();");
  }
}
