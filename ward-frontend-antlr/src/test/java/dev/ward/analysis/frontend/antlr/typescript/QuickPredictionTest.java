package dev.ward.analysis.frontend.antlr.typescript;

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
import org.antlr.v4.runtime.atn.DecisionInfo;
import org.antlr.v4.runtime.atn.ParseInfo;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.ProfilingATNSimulator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The quick prediction gives the tree of the adaptive prediction, and keeps the lookahead of a
 * long statement short.
 */
class QuickPredictionTest {
  private static final Path FIXTURES = Path.of("src/test/resources/syntax/typescript");
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
          "new Outer.Inner<string>(1).run(2)[3];",
          "a?.b?.c?.(d)?.[e];",
          "first\n(second);",
          "first\n[second].run();",
          "const value = typeof other === 'string' ? void 0 : await load(delete box.key);",
          "label: for (const item of items) { if (item) { continue label; } break; }",
          "let\nvalue = 1;",
          "const enum Level { Low, High }",
          "try { run(); } catch { stop(); } finally { done(); }",
          "do { step(); } while (more());\n(start)();",
          "const made = class { }; class Named<T> extends made { }",
          "x = y as Map<string, number>; z = w as number < 5;",
          "switch (key) { case 1: return; default: throw new Error('none'); }",
          "with (scope) { debugger; }",
          "await.then(run); typeof\nvalue;",
          "run(async (first, second) => first, async function () {}, async item => item, async (x));",
          "export default class Shape { private size = 1; static create(): Shape { return new Shape(); }"
              + " get area() { return 1; } constructor() { this.size = 2; } protected async grow(by) {} }",
          "export const made = new Thing(1); export function build() {} export type * from './x';",
          "export = legacy; class extends Base {}; let = 1; export default class {}",
          "const f = async (x): Promise<void> => {}; const g = async (x): () => void => () => {};",
          "const h = ok ? async (x) : y => y; switch (k) { case async (x): z => z; }",
          "class A { get a(): { b: number } { return 1; } set a(v) {} static get #p() { return 2; }"
              + " get 'q'(): number[] { return []; } get r(): Map<string, number> { return m; } }",
          "declare class B { get a(): number; set a(v: number); }",
          "if (a) if (b) c(); else d(); else e(); if (a) f()\nelse = 1",
          "type A = { a: 1 }; type B = { a: 1 } | C; type D<T> = { a: T } extends E ? F : G;",
          "type H = { a: 1 }[]; type I = { a: 1 }\nconst x = 1; export type J = { a: 1 }; export type { K };",
          "export type * from './m'; export type = 1; type = 2; type\nX = 3;",
          "new a.b.c(1)(2).d;",
          "new a.b;",
          "new a.b(1).c(2); new a.b(1).c; new a.b(1)?.c(2);",
          "new a.b<T>(1);",
          "new (a.b)(1);",
          "new a.b(1)[2];",
          "const x: A = { get: 1, set: 2 }; class C { get(): void {} set = 1; get = 2; }",
      }
  )
  void sourceHasTheTreeOfTheAdaptivePrediction(final String source) {
    assertThat(tree(parser(source))).isEqualTo(tree(adaptiveParser(source)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
          "const load = async (id: string): Promise<number> => { %s };",
          "class Box { get size(): number { %s } set size(value: number) { %s } }",
          "const made = new a.b(1, () => { %s });",
          "if (ready) { run(); } else { %s }",
          "type Config<T> = { %t }; export type Options = { %t };",
      }
  )
  @DisplayName("a construct that read far")
  void constructThatReadFarIsDecidedWithinAFewTokens(final String template) {
    // Given
    final StringBuilder body = new StringBuilder();
    for (int index = 0; index < STATEMENTS; index++) {
      body.append("const value").append(index).append(" = await load(").append(index)
          .append(").then(result => result.body); ");
    }
    final StringBuilder members = new StringBuilder();
    for (int index = 0; index < STATEMENTS; index++) {
      members.append("key").append(index).append("?: (x: string, y: Array<number>) => void; ");
    }
    final String source = template.replace("%s", body).replace("%t", members) + "\n";
    final TypeScriptParser parser = parser(source);
    parser.setProfile(true);

    // When
    parser.program();

    // Then
    final ParserATNSimulator simulator =
        ((QuickPredictingSimulator)parser.getInterpreter()).adaptive();
    final long longest =
        Arrays.stream(new ParseInfo((ProfilingATNSimulator)simulator).getDecisionInfo())
            .mapToLong(decision -> Math.max(decision.SLL_MaxLook, decision.LL_MaxLook))
            .max()
            .orElseThrow();
    assertThat(longest).isLessThanOrEqualTo(SHORT_LOOKAHEAD);
    assertThat(tree(parser(source))).isEqualTo(tree(adaptiveParser(source)));
  }

  @Test
  void longCallbackIsDecidedWithinAFewTokens() {
    final StringBuilder source = new StringBuilder("test('long', async context => {\n");
    for (int index = 0; index < STATEMENTS; index++) {
      source.append("  const value").append(index).append(" = await load(context, ").append(index)
          .append(").then(result => result.body);\n");
      source.append("  try { check(typeof value").append(index)
          .append("); } catch (error) { void report(error); }\n");
    }
    source.append("});\n");
    final TypeScriptParser parser = parser(source.toString());
    parser.setProfile(true);

    parser.program();

    final ParserATNSimulator simulator =
        ((QuickPredictingSimulator)parser.getInterpreter()).adaptive();
    final DecisionInfo[] decisions =
        new ParseInfo((ProfilingATNSimulator)simulator).getDecisionInfo();
    assertThat(parser.getNumberOfSyntaxErrors()).isZero();
    assertThat(
        Arrays.stream(decisions)
            .mapToLong(decision -> Math.max(decision.SLL_MaxLook, decision.LL_MaxLook))
            .max()
            .orElseThrow()
    )
        .isLessThanOrEqualTo(SHORT_LOOKAHEAD);
  }

  private static String tree(final TypeScriptParser parser) {
    final String tree = parser.program().toStringTree(parser);
    return parser.getNumberOfSyntaxErrors() + " " + tree;
  }

  private static TypeScriptParser parser(final String source) {
    final TypeScriptParser parser =
        new TypeScriptParser(
            new CommonTokenStream(new TypeScriptLexer(CharStreams.fromString(source)))
        );
    parser.removeErrorListeners();
    return parser;
  }

  /** A parser whose every decision goes to the simulator of the generated parser. */
  private static TypeScriptParser adaptiveParser(final String source) {
    final TypeScriptParser parser =
        new TypeScriptParser(
            new CommonTokenStream(new TypeScriptLexer(CharStreams.fromString(source)))
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
