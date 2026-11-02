package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ParseResult;
import org.junit.jupiter.api.Test;

/** Grammar predicates and actions for JavaScriptConstruct, exercised through the frontend. */
class JavaScriptConstructTest {

  private static ParseResult parse(final AnalysisLanguage language, final String text) {
    return new AntlrLanguageFrontend().parse(new SourceUnit("sample", language, text));
  }

  @Test
  void javaScriptRegexAfterOperatorAndDivisionAfterIdentifierBothParse() {
    final String text = "var r = /ab+/g;\nvar d = a / b / c;";

    assertThat(parse(AnalysisLanguage.JAVASCRIPT, text).diagnostics()).isEmpty();
  }

  @Test
  void javaScriptTemplateStringWithSubstitutionParses() {
    final String text = "var s = `a${1 + 2}b${`inner ${3}`}`;";

    assertThat(parse(AnalysisLanguage.JAVASCRIPT, text).diagnostics()).isEmpty();
  }

  @Test
  void javaScriptHashBangLineAtStartOfFileParses() {
    assertThat(parse(AnalysisLanguage.JAVASCRIPT, "#!/usr/bin/env node\nvar x = 1;").diagnostics())
        .isEmpty();
  }

  @Test
  void javaScriptReturnWithLineTerminatorParses() {
    assertThat(
            parse(AnalysisLanguage.JAVASCRIPT, "function f() {\n  return\n  1;\n}\n").diagnostics())
        .isEmpty();
  }

  @Test
  void javaScriptPostfixIncrementOnNewLineParses() {
    assertThat(parse(AnalysisLanguage.JAVASCRIPT, "var a = 1;\na\n++a;\n").diagnostics()).isEmpty();
  }

  @Test
  void validJavaScriptConstructsParseWithoutDiagnostics() {
    final String[] snippets = {
      "var o = { get a() { return 1; }, set a(v) {} };",
      "var t = `a${ {k:1}.k }b`;",
      "var a = b\n++c",
      "var r = x ? /a/ : /b/;",
      "label: for (;;) { break label; }",
      "class A { static m() {} }",
      "'use strict'; function f() { var a = 1; }",
      "var y = (a) / 2 / (b);",
    };
    for (final String snippet : snippets) {
      assertThat(parse(AnalysisLanguage.JAVASCRIPT, snippet).diagnostics()).as(snippet).isEmpty();
    }
  }
}
