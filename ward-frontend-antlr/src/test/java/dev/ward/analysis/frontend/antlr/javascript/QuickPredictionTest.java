package dev.ward.analysis.frontend.antlr.javascript;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.atn.ParseInfo;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.ProfilingATNSimulator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The quick prediction of the JavaScript parser gives the tree of the adaptive prediction, and keeps
 * the lookahead of a long statement short.
 */
class QuickPredictionTest {
  private static final Path FIXTURES = Path.of("src/test/resources/syntax/javascript");
  private static final int STATEMENTS = 200;

  /** The longest lookahead allowed for a callback of {@value #STATEMENTS} statements. */
  private static final long SHORT_LOOKAHEAD = 40;

  static List<Path> fixtures() throws IOException {
    try (Stream<Path> files = Files.list(FIXTURES)) {
      return files.sorted().toList();
    }
  }

  @Test
  void everyAnsweredDecisionIsFoundInTheGrammar() {
    assertThat(new QuickPrediction(parser("")).complete()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void fixtureHasTheTreeOfTheAdaptivePrediction(final Path fixture) throws IOException {
    final String source = Files.readString(fixture, StandardCharsets.UTF_8);

    assertThat(tree(parser(source))).isEqualTo(tree(adaptiveParser(source)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
          "new a.b(c);",
          "new (a?.b)();",
          "new Outer.Inner(1).run(2)[3];",
          "new a.b.c(1)(2).d;",
          "new a.b;",
          "new a.b(1).c(2); new a.b(1).c; new a.b(1)?.c(2);",
          "new a.b(1)[2];",
          "a?.b?.c?.(d)?.[e];",
          "first\n(second);",
          "first\n[second].run();",
          "const value = typeof other === 'string' ? void 0 : await load(delete box.key);",
          "label: for (const item of items) { if (item) { continue label; } break; }",
          "let\nvalue = 1;",
          "try { run(); } catch { stop(); } finally { done(); }",
          "do { step(); } while (more());\n(start)();",
          "const made = class { }; class Named extends made { }",
          "switch (key) { case 1: return; default: throw new Error('none'); }",
          "with (scope) { debugger; }",
          "await.then(run); typeof\nvalue;",
          "run(async (first, second) => first, async function () {}, async item => item, async (x));",
          "if (a) if (b) c(); else d(); else e(); if (a) f()\nelse = 1",
          "class A { get a() { return 1; } set a(v) {} static async run() {} }",
          "const x = { get: 1, set: 2, if: 3 }; var y = x.if; x.class = 1;",
      }
  )
  void sourceHasTheTreeOfTheAdaptivePrediction(final String source) {
    assertThat(tree(parser(source))).isEqualTo(tree(adaptiveParser(source)));
  }

  @Test
  @DisplayName("JavaScript gets quick prediction")
  void longJavaScriptFileIsDecidedByQuickPrediction() {
    // Given
    final StringBuilder source = new StringBuilder("test('long', async context => {\n");
    for (int index = 0; index < STATEMENTS; index++) {
      source.append("  const value").append(index).append(" = await load(context, ").append(index)
          .append(").then(result => result.body);\n")
          .append("  try { check(typeof value").append(index)
          .append("); } catch (error) { void report(error); }\n")
          .append("  if (value").append(index).append(") { run(); } else { stop(); }\n");
    }
    source.append("  return new a.b(context, () => { run(); });\n});\n");
    final JavaScriptParser parser = parser(source.toString());
    parser.setProfile(true);

    // When
    parser.program();

    // Then
    assertThat(parser.getInterpreter()).isInstanceOf(QuickPredictingSimulator.class);
    final ParserATNSimulator simulator =
        ((QuickPredictingSimulator)parser.getInterpreter()).adaptive();
    assertThat(parser.getNumberOfSyntaxErrors()).isZero();
    assertThat(
        Arrays.stream(new ParseInfo((ProfilingATNSimulator)simulator).getDecisionInfo())
            .mapToLong(decision -> Math.max(decision.SLL_MaxLook, decision.LL_MaxLook))
            .max()
            .orElseThrow()
    )
        .isLessThanOrEqualTo(SHORT_LOOKAHEAD);
    assertThat(tree(parser(source.toString())))
        .isEqualTo(tree(adaptiveParser(source.toString())));
  }

  private static String tree(final JavaScriptParser parser) {
    final String tree = parser.program().toStringTree(parser);
    return parser.getNumberOfSyntaxErrors() + " " + tree;
  }

  private static JavaScriptParser parser(final String source) {
    final JavaScriptParser parser =
        new JavaScriptParser(
            new CommonTokenStream(new JavaScriptLexer(CharStreams.fromString(source)))
        );
    parser.removeErrorListeners();
    return parser;
  }

  /** A parser whose every decision goes to the simulator of the generated parser. */
  private static JavaScriptParser adaptiveParser(final String source) {
    final JavaScriptParser parser =
        new JavaScriptParser(
            new CommonTokenStream(new JavaScriptLexer(CharStreams.fromString(source)))
        ) {
          @Override
          public ParserATNSimulator getInterpreter() {
            return _interp;
          }
        };
    parser.removeErrorListeners();
    return parser;
  }
}
