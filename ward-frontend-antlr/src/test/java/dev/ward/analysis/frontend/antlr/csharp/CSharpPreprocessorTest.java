package dev.ward.analysis.frontend.antlr.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CSharpPreprocessorTest {
  private static final String IDENTIFIER = "Simple_Identifier";
  private static final int CLASS_LINE = 5;

  private static List<String> names(final String source) {
    final ParseResult result =
        new AntlrLanguageFrontend().parse(new SourceUnit("p.cs", AnalysisLanguage.CSHARP, source));
    assertThat(result.diagnostics()).isEmpty();
    final List<String> found = new ArrayList<>();
    collect(result.tree(), found);
    return found;
  }

  private static void collect(final SyntaxNode node, final List<String> found) {
    if (IDENTIFIER.equals(node.kind())) {
      found.add(node.fields().get("text"));
    }
    node.children().forEach(child -> collect(child, found));
  }

  @Test
  void aDefinedSymbolSelectsItsBranchAndSkipsTheOthers() {
    final List<String> found =
        names(
            "#define A\nclass C {\n#if A && !B\n int x;\n#elif B\n int y;\n#else\n int z;\n#endif\n}\n"
        );

    assertThat(found).containsExactly("C", "x");
  }

  @Test
  void anUndefinedSymbolFallsThroughToElifAndElse() {
    assertThat(
        names(
            "class C {\n#if A\n int x;\n#elif (B || !A) == true\n int y;\n#else\n int z;\n#endif\n}\n"
        )
    )
        .containsExactly("C", "y");
    assertThat(names("class C {\n#if A\n int x;\n#elif B\n int y;\n#else\n int z;\n#endif\n}\n"))
        .containsExactly("C", "z");
  }

  @Test
  void undefRemovesASymbolAndNestedBlocksAreSkippedWhole() {
    final List<String> found =
        names(
            "#define A\n#undef A\nclass C {\n#if A\n#if B\n int x;\n#endif\n int y;\n#else\n int z;\n#endif\n}\n"
        );

    assertThat(found).containsExactly("C", "z");
  }

  @Test
  void aDefineInsideASkippedBranchIsIgnored() {
    final List<String> found =
        names(
            "class C {\n#if NEVER\n#define LATER\n#endif\n#if LATER\n int x;\n#endif\n int y;\n}\n"
        );

    assertThat(found).containsExactly("C", "y");
  }

  @Test
  void aSourceWithoutDirectivesParsesTwiceWithoutLeakingState() {
    assertThat(names("#define A\nclass C {\n#if A\n int x;\n#endif\n}\n")).containsExactly(
        "C",
        "x"
    );
    assertThat(names("class C {\n#if A\n int x;\n#endif\n}\n")).containsExactly("C");
  }

  @Test
  void aByteOrderMarkAtTheStartOfTheFileIsWhitespace() {
    assertThat(names("\uFEFFclass C {}\n")).containsExactly("C");
  }

  @Test
  void theLinesAfterAFalseBranchKeepTheirNumber() {
    final ParseResult result =
        new AntlrLanguageFrontend()
            .parse(
                new SourceUnit(
                    "p.cs",
                    AnalysisLanguage.CSHARP,
                    "#if X\nint a;\nint b;\n#endif\nclass C {}\n"
                )
            );

    assertThat(lineOf(result.tree(), "C")).isEqualTo(CLASS_LINE);
  }

  private static int lineOf(final SyntaxNode node, final String name) {
    if (IDENTIFIER.equals(node.kind()) && name.equals(node.fields().get("text"))) {
      return node.span().startLine();
    }
    return node.children().stream()
        .mapToInt(child -> lineOf(child, name))
        .filter(line -> line > 0)
        .findFirst()
        .orElse(0);
  }
}
