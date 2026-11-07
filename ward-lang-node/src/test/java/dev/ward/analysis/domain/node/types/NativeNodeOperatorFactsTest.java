package dev.ward.analysis.domain.node.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Checks the types of operators and literals. */
class NativeNodeOperatorFactsTest {
  private static final String NUMBER = "number";
  private static final String STRING = "string";
  private static final String BOOLEAN = "boolean";
  private static final String BIGINT = "bigint";
  private static final String UNKNOWN = "-";
  private static final String UNIT =
      """
      const n = 1;
      let m = 2;
      const s = "a";
      let t = "b";
      const big = 10n;
      const a1 = typeof n;
      const a2 = void n;
      const a3 = !n;
      const a4 = -n;
      const a5 = -m;
      const a6 = ~big;
      const a7 = m++;
      const b1 = m - 1;
      const b2 = big - big;
      const b3 = big + big;
      const b4 = s + m;
      const b5 = m < 3;
      const b6 = m === 3 && s;
      const b7 = t || "x";
      const b8 = n ?? "z";
      const c1 = m > 1 ? "x" : "y";
      const c2 = m > 1 ? 1 : 1;
      const e1 = [1, "a"];
      const e2 = [];
      const e3 = { a: 1, b: [true] };
      const g1 = m as unknown;
      const g2 = t!;
      const h1 = `x${m}`;
      const h2 = /re/;
      const i1 = m = 5;
      const i2 = delete t;
      const i3 = m ** 2;
      const j1 = (m, t);
      const k1 = big < big;
      const k2 = m + t;
      const k3 = m + big;
      """;

  private static String type(final String token) {
    return TypeScenarios.type(TypeScenarios.project(UNIT), token);
  }

  @Test
  void prefixOperatorsHaveTheirResultTypes() {
    assertThat(type("typeof n")).isEqualTo(STRING);
    assertThat(type("void n")).isEqualTo("undefined");
    assertThat(type("!n")).isEqualTo(BOOLEAN);
    assertThat(type("delete t")).isEqualTo(BOOLEAN);
    assertThat(type("-n")).isEqualTo("-1");
    assertThat(type("-m")).isEqualTo(NUMBER);
    assertThat(type("~big")).isEqualTo(BIGINT);
    assertThat(type("m++")).isEqualTo(NUMBER);
  }

  @Test
  void binaryOperatorsFollowTheirOperandTypes() {
    assertThat(type("m - 1")).isEqualTo(NUMBER);
    assertThat(type("m ** 2")).isEqualTo(NUMBER);
    assertThat(type("big - big")).isEqualTo(BIGINT);
    assertThat(type("big + big")).isEqualTo(BIGINT);
    assertThat(type("s + m")).isEqualTo(STRING);
    assertThat(type("m + t")).isEqualTo(STRING);
    assertThat(type("m + big")).isEqualTo(UNKNOWN);
    assertThat(type("m < 3")).isEqualTo(BOOLEAN);
    assertThat(type("big < big")).isEqualTo(BOOLEAN);
  }

  @Test
  void logicalOperatorsAndConditionalsJoinTheirBranches() {
    assertThat(type("m === 3 && s")).isEqualTo(UNKNOWN);
    assertThat(type("t || \"x\"")).startsWith(STRING);
    assertThat(type("n ?? \"z\"")).isEqualTo("1 | \"z\"");
    assertThat(type("m > 1 ? \"x\" : \"y\"")).isEqualTo("\"x\" | \"y\"");
    assertThat(type("m > 1 ? 1 : 1")).isEqualTo("1");
  }

  @Test
  void literalsAndAssertionsHaveTheirTypes() {
    assertThat(type("[1, \"a\"]")).isEqualTo("(number | string)[]");
    assertThat(type("[]")).isEqualTo(UNKNOWN);
    assertThat(type("{ a: 1, b: [true] }")).isEqualTo("{ a: number; b: boolean[]; }");
    assertThat(type("m as unknown")).isEqualTo("unknown");
    assertThat(type("t!")).isEqualTo(STRING);
    assertThat(type("`x${m}`")).isEqualTo(STRING);
    assertThat(type("/re/")).isEqualTo("RegExp");
    assertThat(type("10n")).isEqualTo(BIGINT);
    assertThat(type("m = 5")).isEqualTo("5");
    assertThat(type("(m, t)")).isEqualTo(STRING);
  }
}
