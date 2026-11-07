package dev.ward.analysis.domain.node.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks that types read from tokens print the way the compiler prints them. */
class TyParserTest {

  private static String printed(final String source) {
    return TyParser.parse(tokens(source)).print();
  }

  private static List<String> tokens(final String source) {
    return Arrays.asList(source.trim().split("\\s+"));
  }

  @Test
  void printsPrimitivesLiteralsUnionsAndArrays() {
    assertThat(printed("string")).isEqualTo("string");
    assertThat(printed("\"a\" | 1 | true")).isEqualTo("\"a\" | 1 | true");
    assertThat(printed("string [ ]")).isEqualTo("string[]");
    assertThat(printed("( string | number ) [ ]")).isEqualTo("(string | number)[]");
    assertThat(printed("A & B")).isEqualTo("A & B");
  }

  @Test
  void printsGenericsAndQualifiedNames() {
    assertThat(printed("Promise < Map < string , number [ ] > >"))
        .isEqualTo("Promise<Map<string, number[]>>");
    assertThat(printed("NS . Inner < T >")).isEqualTo("NS.Inner<T>");
  }

  @Test
  void printsFunctionsObjectsAndTuples() {
    assertThat(printed("( a : number , b ? : string ) => void"))
        .isEqualTo("(a: number, b?: string) => void");
    assertThat(printed("< T > ( v : T ) => T")).isEqualTo("<T>(v: T) => T");
    assertThat(printed("{ a : number ; b ? : string }")).isEqualTo("{ a: number; b?: string; }");
    assertThat(printed("{ }")).isEqualTo("{}");
    assertThat(printed("[ a : string , b ? : number ]")).isEqualTo("[string, number]");
    assertThat(printed("( ... rest : number [ ] ) => number"))
        .isEqualTo("(...rest: number[]) => number");
  }

  @Test
  void typePredicatesAreBooleanAndAssertionsAreVoid() {
    assertThat(printed("( x : unknown ) => x is string")).isEqualTo("(x: unknown) => boolean");
    assertThat(printed("( x : unknown ) => asserts x")).isEqualTo("(x: unknown) => void");
  }

  @Test
  void whatNeedsFullCheckingIsUnknown() {
    assertThat(TyParser.parse(tokens("T extends string ? 1 : 2")).isKnown()).isFalse();
    assertThat(TyParser.parse(tokens("keyof T")).isKnown()).isFalse();
    assertThat(TyParser.parse(tokens("typeof value")).isKnown()).isFalse();
    assertThat(TyParser.parse(tokens("{ [ K in keyof T ] : T [ K ] }")).isKnown()).isFalse();
    assertThat(TyParser.parse(List.of()).isKnown()).isFalse();
    assertThat(TyParser.parse(tokens("string number")).isKnown()).isFalse();
  }
}
