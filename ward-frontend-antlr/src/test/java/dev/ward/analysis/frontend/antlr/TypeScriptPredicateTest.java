package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import org.junit.jupiter.api.Test;

/** Grammar predicates and actions for TypeScriptPredicate, exercised through the frontend. */
class TypeScriptPredicateTest {

  private static ParseResult parse(final AnalysisLanguage language, final String text) {
    return new AntlrLanguageFrontend().parse(new SourceUnit("sample", language, text));
  }

  @Test
  void typeScriptTemplateStringAndRegexParse() {
    final String text = "const s: string = `a${1 + 2}b`;\nconst r = /x+/i;\nconst d = a / b;";

    assertThat(parse(AnalysisLanguage.TYPESCRIPT, text).diagnostics()).isEmpty();
  }

  @Test
  void typeScriptStrictModeSourceParsesToATree() {
    assertThat(parse(AnalysisLanguage.TYPESCRIPT, "'use strict';\nlet x = 010;").tree())
        .isNotNull();
  }

  @Test
  void typeScriptReturnWithLineTerminatorParses() {
    assertThat(
            parse(AnalysisLanguage.TYPESCRIPT, "function f(): number {\n  return\n  1;\n}\n")
                .diagnostics())
        .isEmpty();
  }

  @Test
  void validTypeScriptConstructsParseWithoutDiagnostics() {
    final String[] snippets = {
      "const f = (x: number): x is number => x > 0;",
      "let t = `outer ${`inner ${1}`} end`;",
      "let t = `a ${ {k: 1}.k } b`;",
      "function f() { if (x) { return } }",
      "class A { private x = 1; get y(): number { return this.x; } }",
      "let v = a\n++b;",
      "type T = {a: number}\nlet u = 1",
      "enum E { A, B }",
      "let r = /x/.test(s) ? 1 : 2;",
      "abstract class B { abstract m(): void; }",
      "interface I { a?: string; }",
      "export default function () {}",
      "let n = x as unknown as number;",
      "for (const k of [1]) {}",
      "label: for (;;) { break label; }",
    };
    for (final String snippet : snippets) {
      assertThat(parse(AnalysisLanguage.TYPESCRIPT, snippet).diagnostics()).as(snippet).isEmpty();
    }
  }
}
