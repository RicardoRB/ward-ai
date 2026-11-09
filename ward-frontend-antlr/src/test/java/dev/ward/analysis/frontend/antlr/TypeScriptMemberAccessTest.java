package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** An identifier followed by an operator, a call or a subscript parses as an expression. */
class TypeScriptMemberAccessTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "const x = config['timeout'];",
        "const x = config.b['c'];",
        "const x = f()['x'];",
        "const x = a as T;",
        "const x = a + b;",
        "const x = a[i];",
        "const x = a(b);",
      })
  void identifierExpressionParsesWithoutSwallowingTheNextOperand(final String statement) {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    for (final String path : List.of("member.ts", "member.tsx")) {
      final ParseResult result = frontend.parse(unit(path, statement));

      assertThat(result.diagnostics()).as("%s: %s", path, statement).isEmpty();
      assertThat(kindsOf(result.tree()))
          .as("%s: %s", path, statement)
          .doesNotContain("arrayLiteral");
    }
  }

  @Test
  void bareBlockInStatementPositionIsABlockNotAnObjectLiteral() {
    final ParseResult result =
        new AntlrLanguageFrontend()
            .parse(
                new SourceUnit(
                    "block.ts", AnalysisLanguage.TYPESCRIPT, "function f() { { return 2 } }\n"));

    assertThat(result.diagnostics()).isEmpty();
    assertThat(kindsOf(result.tree())).contains("block").doesNotContain("objectLiteral");
  }

  @Test
  void statementStartingWithSubscriptIsAnExpressionStatement() {
    final ParseResult result =
        new AntlrLanguageFrontend()
            .parse(new SourceUnit("subscript.ts", AnalysisLanguage.TYPESCRIPT, "node['x'];\n"));

    assertThat(result.diagnostics()).isEmpty();
    assertThat(kindsOf(result.tree()))
        .contains("expressionStatement")
        .doesNotContain("variableStatement", "arrayLiteral");
  }

  private static SourceUnit unit(final String path, final String statement) {
    return new SourceUnit(path, AnalysisLanguage.TYPESCRIPT, statement + "\n");
  }

  private static List<String> kindsOf(final SyntaxNode node) {
    return Stream.concat(
            Stream.of(node.kind()),
            node.children().stream().flatMap(child -> kindsOf(child).stream()))
        .toList();
  }
}
