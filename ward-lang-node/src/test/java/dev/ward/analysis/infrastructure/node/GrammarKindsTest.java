package dev.ward.analysis.infrastructure.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Fails first when the pinned grammars stop naming the nodes and tokens the structural rules read.
 * TypeScript writes class methods as property members and arrow functions as declarations; the
 * JavaScript grammar writes class methods as method definitions.
 */
class GrammarKindsTest {

  private static final String TYPESCRIPT_SAMPLE =
      """
      function helper(p: number) { return p; }
      class Account {
        total(a: number, ...rest: number[]) {
          if (a && rest.length || a ?? a) { return a ? 1 : 2; } else if (a) { void a; }
          for (const x of rest) { void x; }
          switch (a) { case 1: break; default: break; }
          try { void a; } catch (error) { void error; }
          const f = (b: number) => b;
          const g = function () { return a; };
          return f(g());
        }
      }
      """;

  private static final String JAVASCRIPT_SAMPLE =
      """
      class Account {
        total(a, b) { return a; }
      }
      function total(a, b) {
        if (a && b || a ?? b) { return a ? 1 : 2; } else if (a) { void a; }
        for (const x of b) { void x; }
        switch (a) { case 1: break; default: break; }
        try { void a; } catch (error) { void error; }
        const f = (c) => c;
        const g = function () { return a; };
        return f(g());
      }
      """;

  private static final Set<String> SHARED_KINDS =
      Set.of(
          "functionDeclaration",
          "anonymousFunction",
          "functionBody",
          "arrowFunctionBody",
          "arrowFunctionParameters",
          "ifStatement",
          "iterationStatement",
          "switchStatement",
          "caseClause",
          "tryStatement",
          "catchProduction",
          "block",
          "singleExpression",
          "variableDeclaration",
          "'&&'",
          "'||'",
          "'??'",
          "'?'",
          "Identifier",
          "EOF");

  @Test
  void typeScriptGrammarNamesEveryKindTheRulesRead() {
    assertThat(kindsOf(AnalysisLanguage.TYPESCRIPT, TYPESCRIPT_SAMPLE))
        .containsAll(SHARED_KINDS)
        .contains(
            "arrowFunctionDeclaration",
            "functionDeclaration",
            "propertyMemberDeclaration",
            "formalParameterList",
            "parameter");
  }

  @Test
  void javaScriptGrammarNamesEveryKindTheRulesRead() {
    assertThat(kindsOf(AnalysisLanguage.JAVASCRIPT, JAVASCRIPT_SAMPLE))
        .containsAll(SHARED_KINDS)
        .contains(
            "functionDeclaration", "methodDefinition", "formalParameterList", "formalParameterArg");
  }

  private static Set<String> kindsOf(final AnalysisLanguage language, final String text) {
    final SourceUnit unit = new SourceUnit("src/sample", language, text);
    final SyntaxNode tree = new AntlrLanguageFrontend().parse(unit).tree();
    final Set<String> kinds = new HashSet<>();
    collect(tree, kinds);
    return kinds;
  }

  private static void collect(final SyntaxNode node, final Set<String> kinds) {
    kinds.add(node.kind());
    for (final SyntaxNode child : node.children()) {
      collect(child, kinds);
    }
  }
}
